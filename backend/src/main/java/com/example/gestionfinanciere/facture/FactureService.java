package com.example.gestionfinanciere.facture;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.dossier.Dossier;
import com.example.gestionfinanciere.dossier.AccesDossier;
import com.example.gestionfinanciere.dossier.DossierRepository;
import com.example.gestionfinanciere.facture.lecture.DemandeLecture;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.Loggable;

// US-12 Factures d'un dossier, US-13 aperçu et téléchargement. L'appelant vérifie avant que la
// personne connectée est un collaborateur d'un cabinet ; ici, le dossier doit être celui de son cabinet.
@Service
@Loggable
public class FactureService {

	private static final String CHAMP_DOSSIER = "dossierId";
	private static final String CHAMP_FACTURE = "factureId";

	// US-51 RG3 : longueur de la colonne motif_a_completer.
	static final int LONGUEUR_MOTIF_MAX = 255;

	private final FactureRepository factures;
	private final ChangementStatutFactureRepository historique;
	private final ChampLuRepository champsLus;
	private final DossierRepository dossiers;
	private final ApplicationEventPublisher evenements;
	private final Clock horloge;

	public FactureService(FactureRepository factures, ChangementStatutFactureRepository historique,
			ChampLuRepository champsLus, DossierRepository dossiers, ApplicationEventPublisher evenements, Clock horloge) {
		this.factures = factures;
		this.historique = historique;
		this.champsLus = champsLus;
		this.evenements = evenements;
		this.dossiers = dossiers;
		this.horloge = horloge;
	}

	// RG1 : achats ou ventes du dossier ; vide si le dossier n'existe pas ou est d'un autre cabinet.
	@Transactional(readOnly = true)
	public Optional<ListeFactures> lister(Compte collaborateur, Long dossierId, Facture.Sens sens) {
		Optional<Dossier> dossier = dossierDuCabinet(collaborateur, dossierId);
		if (dossier.isEmpty()) {
			refus("facture.liste", collaborateur, dossierId, null);
			return Optional.empty();
		}
		List<LigneFacture> lignes = factures.findByDossierIdAndSensOrderByDeposeeLeDescIdDesc(dossierId, sens)
				.stream().map(LigneFacture::de).toList();
		EvenementMetier.de("facture.liste", "consultee").avec(collaborateur.identifiantsPourLogs())
				.avec(CHAMP_DOSSIER, dossierId).avec("sens", sens).avec("nombre", lignes.size()).info();
		return Optional.of(new ListeFactures(lignes));
	}

	// US-13 : nom et type du fichier, pour la page d'aperçu ; vide sans droits sur le dossier.
	@Transactional(readOnly = true)
	public Optional<VueFacture> consulter(Compte collaborateur, Long dossierId, Long factureId) {
		Optional<Facture> facture = factureDuCabinet(collaborateur, dossierId, factureId);
		if (facture.isEmpty()) {
			refus("facture.consultation", collaborateur, dossierId, factureId);
		}
		return facture.map(f -> VueFacture.de(f, champsLus.findByFactureId(f.getId())));
	}

	/**
	 * US-13 RG1 et RG2 : fichier d'origine, pour l'aperçu ou le téléchargement. Quelqu'un du
	 * cabinet l'ouvre : le badge « Nouvelle » disparaît (US-12 RG3). Vide sans droits sur le
	 * dossier (RG3) ou si la facture n'a pas de fichier. Jamais le contenu dans les logs.
	 */
	@Transactional
	public Optional<FichierFacture> ouvrir(Compte collaborateur, Long dossierId, Long factureId,
			boolean telechargement) {
		String event = telechargement ? "facture.telechargement" : "facture.apercu";
		Optional<Facture> facture = factureDuCabinet(collaborateur, dossierId, factureId)
				.filter(trouvee -> trouvee.getContenu() != null);
		if (facture.isEmpty()) {
			refus(event, collaborateur, dossierId, factureId);
			return Optional.empty();
		}
		facture.get().marquerOuverte(horloge.instant());
		EvenementMetier.de(event, "ouverte").avec(collaborateur.identifiantsPourLogs()).avec(CHAMP_DOSSIER, dossierId)
				.avec(CHAMP_FACTURE, factureId).info();
		return facture.map(FichierFacture::de);
	}

	/**
	 * US-52 : un fichier importé par le cabinet dans l'onglet du dossier (RG1). Mêmes contrôles que
	 * le dépôt du dirigeant (RG2, US-15 RG1 : vrai type et taille) ; un comptable n'importe que dans
	 * ses dossiers (RG3, US-20). La facture apparaît tout de suite, « Reçue » et « Import cabinet »
	 * (RG4). Jamais le nom ni le contenu du fichier dans les logs.
	 */
	@Transactional
	public ResultatImport importer(Compte collaborateur, Long dossierId, Facture.Sens sens, String nomFichier,
			byte[] contenu) {
		ResultatImport resultat = importerSansJournal(collaborateur, dossierId, sens, nomFichier, contenu);
		EvenementMetier evenement = EvenementMetier.de("facture.import", resultat.statut())
				.avec(collaborateur.identifiantsPourLogs()).avec(CHAMP_DOSSIER, dossierId).avec("sens", sens)
				.avec("taille", contenu == null ? 0 : contenu.length);
		if (resultat.facture() != null) {
			evenement.avec(CHAMP_FACTURE, resultat.facture().id());
		}
		evenement.journaliser(resultat.statut() == ResultatImport.Statut.IMPORTEE);
		return resultat;
	}

	private ResultatImport importerSansJournal(Compte collaborateur, Long dossierId, Facture.Sens sens,
			String nomFichier, byte[] contenu) {
		Optional<Dossier> dossier = dossierDuCabinet(collaborateur, dossierId);
		if (dossier.isEmpty()) {
			return ResultatImport.refuse(ResultatImport.Statut.DOSSIER_INTROUVABLE);
		}
		if (contenu != null && contenu.length > DepotFactureService.TAILLE_MAX) {
			return ResultatImport.refuse(ResultatImport.Statut.TROP_VOLUMINEUX);
		}
		Optional<String> type = TypeFichier.reconnaitre(contenu);
		if (type.isEmpty()) {
			return ResultatImport.refuse(ResultatImport.Statut.FORMAT_REFUSE);
		}
		Facture nouvelle = Facture.importee(dossier.get(), sens, DepotFactureService.nom(nomFichier), type.get(),
				horloge.instant(), collaborateur, contenu);
		// US-53 RG1 : une facture fournisseur importée par le cabinet est lue comme un dépôt du dirigeant.
		boolean aLire = nouvelle.demarrerLecture();
		Facture facture = factures.save(nouvelle);
		if (aLire) {
			evenements.publishEvent(new DemandeLecture(facture.getId()));
		}
		return ResultatImport.importee(LigneFacture.de(facture));
	}

	/**
	 * US-51 : le collaborateur change le statut d'une facture de son cabinet. RG2 : seuls les passages
	 * autorisés ; RG3 : « À compléter » exige un motif ; RG4 : chaque changement garde son auteur et sa
	 * date. Jamais le motif dans les logs (texte libre).
	 */
	@Transactional
	public ResultatStatut changerStatut(Compte collaborateur, Long dossierId, Long factureId, Facture.Statut nouveau,
			String motif) {
		Optional<Facture> trouvee = factureDuCabinet(collaborateur, dossierId, factureId);
		if (trouvee.isEmpty()) {
			refus("facture.statut", collaborateur, dossierId, factureId);
			return ResultatStatut.refuse(ResultatStatut.Issue.INTROUVABLE);
		}
		Facture facture = trouvee.get();
		Facture.Statut ancien = facture.getStatut();
		String motifNettoye = motif == null ? "" : motif.strip();
		ResultatStatut.Issue issue;
		if (!ancien.suivants().contains(nouveau)) {
			issue = ResultatStatut.Issue.PASSAGE_INTERDIT;
		} else if (nouveau == Facture.Statut.A_COMPLETER && motifNettoye.isEmpty()) {
			issue = ResultatStatut.Issue.MOTIF_MANQUANT;
		} else if (motifNettoye.length() > LONGUEUR_MOTIF_MAX) {
			issue = ResultatStatut.Issue.MOTIF_TROP_LONG;
		} else {
			issue = ResultatStatut.Issue.CHANGE;
		}
		EvenementMetier evenement = EvenementMetier.de("facture.statut", issue).avec(collaborateur.identifiantsPourLogs())
				.avec(CHAMP_DOSSIER, dossierId).avec(CHAMP_FACTURE, factureId).avec("ancienStatut", ancien)
				.avec("nouveauStatut", nouveau);
		if (issue != ResultatStatut.Issue.CHANGE) {
			evenement.warn();
			return ResultatStatut.refuse(issue);
		}
		String motifGarde = nouveau == Facture.Statut.A_COMPLETER ? motifNettoye : null;
		facture.changerStatut(nouveau, motifGarde);
		historique.save(new ChangementStatutFacture(facture, ancien, nouveau, motifGarde, collaborateur,
				horloge.instant()));
		evenement.info();
		return new ResultatStatut(issue, LigneFacture.de(facture));
	}

	private Optional<Dossier> dossierDuCabinet(Compte collaborateur, Long dossierId) {
		return AccesDossier.dossierAccessible(dossiers, collaborateur, dossierId);
	}

	private Optional<Facture> factureDuCabinet(Compte collaborateur, Long dossierId, Long factureId) {
		return dossierDuCabinet(collaborateur, dossierId)
				.flatMap(dossier -> factures.findByIdAndDossierId(factureId, dossier.getId()));
	}

	// Refus d'accès : warn.
	private static void refus(String event, Compte collaborateur, Long dossierId, Long factureId) {
		EvenementMetier.de(event, "introuvable").avec(collaborateur.identifiantsPourLogs())
				.avec(CHAMP_DOSSIER, dossierId).avec(CHAMP_FACTURE, factureId).warn();
	}

}
