package com.example.gestionfinanciere.cabinet;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.CompteRepository;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.Loggable;

// US-36 Créer le compte de son cabinet, en 2 étapes : informations, puis « Cabinet prêt ».
// Le code de confirmation reçu par email a été retiré : le cabinet et son administrateur sont
// créés dès que le formulaire est valide.
@Service
@Loggable
public class InscriptionService {

	static final String EVENEMENT = "inscription.creee";

	private final CabinetRepository cabinets;
	private final CompteRepository comptes;
	private final PasswordEncoder encodeur;
	private final Clock horloge;

	public InscriptionService(CabinetRepository cabinets, CompteRepository comptes, PasswordEncoder encodeur,
			Clock horloge) {
		this.cabinets = cabinets;
		this.comptes = comptes;
		this.encodeur = encodeur;
		this.horloge = horloge;
	}

	// Vérifie le formulaire (RG2), puis crée le cabinet et son administrateur (CA1).
	@Transactional
	public ResultatInscription creer(DemandeInscription demande) {
		ResultatInscription resultat = verifierEtCreer(demande);
		EvenementMetier evenement = EvenementMetier.de(EVENEMENT, resultat.statut())
				.avecChampsInvalides(resultat.erreurs());
		if (resultat.compte() != null) {
			evenement.avec(resultat.compte().identifiantsPourLogs());
		}
		evenement.journaliser(resultat.statut() == ResultatInscription.Statut.CABINET_CREE);
		return resultat;
	}

	private ResultatInscription verifierEtCreer(DemandeInscription demande) {
		Map<String, String> erreurs = ValidationInscription.erreurs(demande);
		if (!erreurs.isEmpty()) {
			return ResultatInscription.invalide(erreurs);
		}
		FormulaireValide formulaire = ValidationInscription.remettreEnForme(demande);
		if (cabinets.existsBySiren(formulaire.siren())) {
			return ResultatInscription.sirenDejaUtilise(); // CA2
		}
		if (comptes.findByEmail(formulaire.email()).isPresent()) {
			return ResultatInscription.emailDejaUtilise();
		}
		Instant maintenant = horloge.instant();
		Cabinet cabinet = cabinets.save(new Cabinet(formulaire.nomCabinet(), formulaire.siren(),
				formulaire.nombreDossiers(), formulaire.codeParrainage(), maintenant, maintenant));
		Compte administrateur = comptes.save(Compte.administrateurDe(cabinet, formulaire.email(),
				encodeur.encode(formulaire.motDePasse()), formulaire.prenom() + " " + formulaire.nom()));
		return ResultatInscription.cabinetCree(administrateur);
	}

}
