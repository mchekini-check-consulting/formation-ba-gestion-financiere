package com.example.gestionfinanciere.dossier;

import java.time.Clock;
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
import com.example.gestionfinanciere.logging.Loggable;

// US-10 Créer un dossier client. L'appelant vérifie avant que la personne connectée est un
// collaborateur d'un cabinet : le dossier est toujours rangé dans son cabinet.
@Service
@Loggable
public class DossierService {

	private static final Logger log = LoggerFactory.getLogger(DossierService.class);
	private static final Pattern CHIFFRES = Pattern.compile("^\\d+$");

	private final DossierRepository dossiers;
	private final Clock horloge;

	public DossierService(DossierRepository dossiers, Clock horloge) {
		this.dossiers = dossiers;
		this.horloge = horloge;
	}

	// CA1 : crée le dossier (RG1) ; CA2 : refuse un SIREN déjà suivi par le cabinet (RG2).
	// Sans transaction englobante : si deux créations simultanées passent la vérification, la
	// contrainte unique de la base refuse la seconde, traitée comme un doublon.
	public ResultatCreationDossier creer(Compte collaborateur, DemandeDossier demande) {
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
			log.atInfo().addKeyValue("dossier", dossier.getId()).log("dossier créé");
			return ResultatCreationDossier.cree(VueDossier.de(dossier));
		} catch (DataIntegrityViolationException e) {
			return doublon();
		}
	}

	// RG3 : le dossier s'ouvre après sa création. Vide s'il n'existe pas ou appartient à un
	// autre cabinet.
	@Transactional(readOnly = true)
	public Optional<VueDossier> consulter(Compte collaborateur, Long id) {
		Optional<VueDossier> dossier = dossiers.findByIdAndCabinetId(id, collaborateur.getCabinet().getId())
				.map(VueDossier::de);
		if (dossier.isPresent()) {
			log.atInfo().addKeyValue("dossier", id).log("dossier ouvert");
		} else {
			log.atWarn().addKeyValue("dossier", id).log("accès refusé : dossier introuvable ou d'un autre cabinet");
		}
		return dossier;
	}

	/**
	 * US-09 Liste des dossiers du cabinet du collaborateur (RG3 : jamais ceux d'un autre cabinet),
	 * filtrée par raison sociale ou SIREN si {@code recherche} est rempli (RG2).
	 * US-20 : le filtre « dossiers du comptable » s'ajoutera ici, selon son rôle dans le cabinet.
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
		List<LigneDossier> lignes = dossiers.lister(collaborateur.getCabinet().getId(), motif, motifSiren);
		log.atInfo().addKeyValue("nombre", lignes.size()).addKeyValue("recherche", filtre)
				.log("liste des dossiers consultée");
		return lignes;
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
