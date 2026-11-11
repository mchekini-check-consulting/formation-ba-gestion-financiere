package com.example.gestionfinanciere.dossier;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.cabinet.ValidationInscription;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.RoleCabinet;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.Loggable;

// US-10 Créer un dossier client. L'appelant vérifie avant que la personne connectée est un
// collaborateur d'un cabinet : le dossier est toujours rangé dans son cabinet.
@Service
@Loggable
public class DossierService {

	private static final Logger log = LoggerFactory.getLogger(DossierService.class);
	private static final Pattern CHIFFRES = Pattern.compile("^\\d+$");
	// Champ métier des logs : numéro du dossier.
	private static final String CHAMP_DOSSIER = "dossier";

	private final DossierRepository dossiers;
	private final AttributionDossierRepository attributions;
	private final Clock horloge;

	public DossierService(DossierRepository dossiers, AttributionDossierRepository attributions, Clock horloge) {
		this.dossiers = dossiers;
		this.attributions = attributions;
		this.horloge = horloge;
	}

	// CA1 : crée le dossier (RG1) ; CA2 : refuse un SIREN déjà suivi par le cabinet (RG2).
	// Sans transaction englobante : si deux créations simultanées passent la vérification, la
	// contrainte unique de la base refuse la seconde, traitée comme un doublon.
	public ResultatCreationDossier creer(Compte collaborateur, DemandeDossier demande) {
		ResultatCreationDossier resultat = creerSansJournal(collaborateur, demande);
		EvenementMetier evenement = EvenementMetier.de("dossier.creation", resultat.statut())
				.avec(collaborateur.identifiantsPourLogs()).avecChampsInvalides(resultat.erreurs());
		if (resultat.dossier() != null) {
			evenement.avec("dossierId", resultat.dossier().id());
		}
		evenement.journaliser(resultat.statut() == ResultatCreationDossier.Statut.CREE);
		return resultat;
	}

	private ResultatCreationDossier creerSansJournal(Compte collaborateur, DemandeDossier demande) {
		Map<String, String> erreurs = ValidationDossier.erreurs(demande);
		if (!erreurs.isEmpty()) {
			return ResultatCreationDossier.invalide(erreurs);
		}
		Long cabinetId = collaborateur.getCabinet().getId();
		String siren = ValidationInscription.siren(demande.siren());
		if (dossiers.existsByCabinetIdAndSiren(cabinetId, siren)) {
			return doublon();
		}
		try {
			Dossier dossier = dossiers.save(new Dossier(collaborateur.getCabinet(), demande.raisonSociale().trim(),
					siren, collaborateur, horloge.instant()));
			// US-20 RG3 : un dossier créé par un comptable lui est attribué automatiquement.
			if (collaborateur.getRoleCabinet() == RoleCabinet.COMPTABLE) {
				attributions.save(new AttributionDossier(dossier, collaborateur, dossier.getCreeLe()));
			}
			log.atInfo().addKeyValue(CHAMP_DOSSIER, dossier.getId()).log("dossier créé");
			return ResultatCreationDossier.cree(VueDossier.de(dossier));
		} catch (DataIntegrityViolationException e) {
			return doublon();
		}
	}

	// US-10 RG3 : le dossier s'ouvre après sa création. US-11 : en-tête avec l'état de l'espace
	// client (RG1) ; vide s'il n'existe pas ou appartient à un autre cabinet (RG3), ou s'il n'est
	// pas attribué au comptable connecté (US-20 RG2).
	@Transactional(readOnly = true)
	public Optional<VueDossier> consulter(Compte collaborateur, Long id) {
		Optional<VueDossier> dossier = AccesDossier.dossierAccessible(dossiers, collaborateur, id)
				.map(trouve -> VueDossier.de(trouve, EtatEspaceClient.de(dossiers.compterComptesDirigeant(id),
						dossiers.compterComptesDirigeantActifs(id))));
		EvenementMetier.de("dossier.consultation", dossier.isPresent() ? "trouve" : "introuvable")
				.avec(collaborateur.identifiantsPourLogs()).avec("dossierId", id).journaliser(dossier.isPresent());
		if (dossier.isPresent()) {
			log.atInfo().addKeyValue(CHAMP_DOSSIER, id).log("dossier ouvert");
		} else {
			log.atWarn().addKeyValue(CHAMP_DOSSIER, id).log("accès refusé : dossier introuvable ou d'un autre cabinet");
		}
		return dossier;
	}

	/**
	 * US-09 Liste des dossiers du cabinet du collaborateur (RG3 : jamais ceux d'un autre cabinet),
	 * filtrée par raison sociale ou SIREN si {@code recherche} est rempli (RG2).
	 * US-20 : avec les comptables de chaque dossier ; un comptable ne voit que ses dossiers (RG2).
	 */
	@Transactional(readOnly = true)
	public List<LigneDossier> lister(Compte collaborateur, String recherche) {
		boolean filtre = !ValidationInscription.estVide(recherche);
		String motif = "%";
		String motifSiren = "-";
		if (filtre) {
			String texte = recherche.trim().toLowerCase(Locale.ROOT);
			motif = "%" + echapper(texte) + "%";
			String chiffres = ValidationInscription.siren(texte);
			if (CHIFFRES.matcher(chiffres).matches()) {
				motifSiren = "%" + chiffres + "%";
			}
		}
		Long cabinetId = collaborateur.getCabinet().getId();
		List<LigneDossier> lignes = avecComptables(cabinetId, dossiers.lister(cabinetId, motif, motifSiren,
				AccesDossier.voitTousLesDossiers(collaborateur), collaborateur.getId()));
		log.atInfo().addKeyValue("nombre", lignes.size()).addKeyValue("recherche", filtre)
				.log("liste des dossiers consultée");
		return lignes;
	}

	// US-20 : noms des comptables de chaque dossier (colonne « Comptables »).
	private List<LigneDossier> avecComptables(Long cabinetId, List<LigneDossier> lignes) {
		Map<Long, List<String>> noms = new LinkedHashMap<>();
		for (AttributionDossier attribution : attributions.findByCabinetId(cabinetId)) {
			noms.computeIfAbsent(attribution.getDossier().getId(), id -> new ArrayList<>())
					.add(attribution.getComptable().getNomAffiche());
		}
		return lignes.stream().map(ligne -> ligne.avecComptables(noms.getOrDefault(ligne.id(), List.of()))).toList();
	}

	// Un « % » ou « _ » saisi est cherché tel quel, pas comme joker.
	private static String echapper(String texte) {
		return texte.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	// CA2 : refus métier (pas un refus d'accès) : info.
	private static ResultatCreationDossier doublon() {
		log.info("création de dossier refusée : SIREN déjà suivi par le cabinet");
		return ResultatCreationDossier.sirenDejaUtilise();
	}

}
