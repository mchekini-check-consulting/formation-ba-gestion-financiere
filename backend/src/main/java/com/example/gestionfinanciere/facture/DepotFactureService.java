package com.example.gestionfinanciere.facture;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.dossier.Dossier;
import com.example.gestionfinanciere.dossier.DossierRepository;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.Loggable;

/**
 * Espace client du dirigeant : US-15 déposer des factures fournisseurs, US-38 déposer des
 * factures clients, US-16 suivre et supprimer ses dépôts. L'appelant vérifie avant que la
 * personne connectée est le dirigeant actif d'un dossier. Jamais le nom ni le contenu d'un
 * fichier dans les logs.
 */
@Service
@Loggable
public class DepotFactureService {

	// US-15 RG1 : 10 Mo maximum par fichier.
	public static final long TAILLE_MAX = 10L * 1024 * 1024;
	static final int LONGUEUR_NOM_MAX = 255;
	private static final String CHAMP_DOSSIER = "dossierId";
	private static final String CHAMP_FACTURE = "factureId";

	public enum ResultatSuppression {
		SUPPRIMEE,
		INTROUVABLE,
		// US-16 RG3 : déjà ouverte par le cabinet.
		DEJA_OUVERTE
	}

	private final FactureRepository factures;
	private final DossierRepository dossiers;
	private final Clock horloge;

	public DepotFactureService(FactureRepository factures, DossierRepository dossiers, Clock horloge) {
		this.factures = factures;
		this.dossiers = dossiers;
		this.horloge = horloge;
	}

	// US-16 RG1 : les dépôts du dirigeant dans un onglet, les plus récents d'abord.
	@Transactional(readOnly = true)
	public List<FactureDeposee> lister(Compte dirigeant, Facture.Sens sens) {
		List<FactureDeposee> liste = factures
				.findByDossierIdAndSensAndDeposeeParIdOrderByDeposeeLeDescIdDesc(dirigeant.getDossierId(), sens,
						dirigeant.getId())
				.stream().map(FactureDeposee::de).toList();
		EvenementMetier.de("espace_client.factures", "consultees").avec(dirigeant.identifiantsPourLogs())
				.avec(CHAMP_DOSSIER, dirigeant.getDossierId()).avec("sens", sens).avec("nombre", liste.size()).info();
		return liste;
	}

	/**
	 * US-15 / US-38 : un fichier déposé dans l'onglet choisi (RG1 US-38). Le serveur vérifie le vrai
	 * type et la taille (RG1) ; la facture apparaît tout de suite dans le bon onglet du dossier côté
	 * cabinet (US-15 RG3, US-38 RG3), avec le badge « Nouvelle ».
	 */
	@Transactional
	public ResultatDepot deposer(Compte dirigeant, Facture.Sens sens, String nomFichier, byte[] contenu) {
		ResultatDepot resultat = deposerSansJournal(dirigeant, sens, nomFichier, contenu);
		EvenementMetier evenement = EvenementMetier.de("espace_client.depot", resultat.statut())
				.avec(dirigeant.identifiantsPourLogs()).avec(CHAMP_DOSSIER, dirigeant.getDossierId()).avec("sens", sens)
				.avec("taille", contenu == null ? 0 : contenu.length);
		if (resultat.facture() != null) {
			evenement.avec(CHAMP_FACTURE, resultat.facture().id());
		}
		evenement.journaliser(resultat.statut() == ResultatDepot.Statut.DEPOSEE);
		return resultat;
	}

	private ResultatDepot deposerSansJournal(Compte dirigeant, Facture.Sens sens, String nomFichier,
			byte[] contenu) {
		if (contenu != null && contenu.length > TAILLE_MAX) {
			return ResultatDepot.refuse(ResultatDepot.Statut.TROP_VOLUMINEUX);
		}
		Optional<String> type = TypeFichier.reconnaitre(contenu);
		Optional<Dossier> dossier = dossiers.findById(dirigeant.getDossierId());
		if (type.isEmpty() || dossier.isEmpty()) {
			return ResultatDepot.refuse(ResultatDepot.Statut.FORMAT_REFUSE);
		}
		Facture facture = factures.save(new Facture(dossier.get(), sens, nom(nomFichier), type.get(),
				horloge.instant(), dirigeant, contenu));
		return ResultatDepot.deposee(FactureDeposee.de(facture));
	}

	// US-16 CA1 : la facture disparaît de l'espace du dirigeant et du dossier ; RG3 : pas si le cabinet l'a ouverte.
	@Transactional
	public ResultatSuppression supprimer(Compte dirigeant, Long factureId) {
		Optional<Facture> facture = factures.findByIdAndDossierIdAndDeposeeParId(factureId, dirigeant.getDossierId(),
				dirigeant.getId());
		ResultatSuppression resultat;
		if (facture.isEmpty()) {
			resultat = ResultatSuppression.INTROUVABLE;
		} else if (facture.get().getOuverteLe() != null) {
			resultat = ResultatSuppression.DEJA_OUVERTE;
		} else {
			factures.delete(facture.get());
			resultat = ResultatSuppression.SUPPRIMEE;
		}
		EvenementMetier.de("espace_client.suppression", resultat).avec(dirigeant.identifiantsPourLogs())
				.avec(CHAMP_DOSSIER, dirigeant.getDossierId()).avec(CHAMP_FACTURE, factureId)
				.journaliser(resultat == ResultatSuppression.SUPPRIMEE);
		return resultat;
	}

	// Nom affiché : sans chemin ni caractères de contrôle, 255 caractères au plus.
	static String nom(String nomFichier) {
		String nom = nomFichier == null ? "" : nomFichier;
		nom = nom.substring(Math.max(nom.lastIndexOf('/'), nom.lastIndexOf('\\')) + 1);
		nom = nom.replaceAll("\\p{Cntrl}", "").trim();
		if (nom.isEmpty()) {
			nom = "facture";
		}
		return nom.length() > LONGUEUR_NOM_MAX ? nom.substring(0, LONGUEUR_NOM_MAX) : nom;
	}

}
