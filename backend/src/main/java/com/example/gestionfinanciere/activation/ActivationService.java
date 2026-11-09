package com.example.gestionfinanciere.activation;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.cabinet.ValidationInscription;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.Jetons;
import com.example.gestionfinanciere.compte.RegleMotDePasse;
import com.example.gestionfinanciere.equipe.Invitation;
import com.example.gestionfinanciere.equipe.InvitationRepository;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.Loggable;

/**
 * US-08 Activer son compte, depuis le lien d'invitation : dirigeant invité par son cabinet
 * (US-14) ou collaborateur invité par l'administrateur (US-19). RG1 : lien valable 7 jours, une
 * seule fois (et pas annulé par un renvoi). RG2 : mot de passe (règles US-06) et CGU acceptées,
 * pas de mobile. RG3 : compte actif dès le formulaire validé, sans code. Jamais le jeton ni le
 * mot de passe dans les logs.
 */
@Service
@Loggable
public class ActivationService {

	public static final String CGU_NON_ACCEPTEES = "Vous devez accepter les conditions d'utilisation";
	private static final String EVENEMENT = "compte.activation";

	private final InvitationRepository invitations;
	private final PasswordEncoder encodeur;
	private final Clock horloge;

	public ActivationService(InvitationRepository invitations, PasswordEncoder encodeur, Clock horloge) {
		this.invitations = invitations;
		this.encodeur = encodeur;
		this.horloge = horloge;
	}

	// Ouverture du lien : formulaire, ou message (lien expiré, espace déjà activé).
	@Transactional(readOnly = true)
	public ResultatActivation verifier(String jeton) {
		ResultatActivation resultat = etat(jeton);
		if (resultat.statut() != ResultatActivation.Statut.VALABLE) {
			journaliser("compte.activation_lien", resultat);
		}
		return resultat;
	}

	@Transactional
	public ResultatActivation activer(String jeton, String motDePasse, boolean cguAcceptees) {
		ResultatActivation etat = etat(jeton);
		if (etat.statut() != ResultatActivation.Statut.VALABLE) {
			journaliser(EVENEMENT, etat);
			return etat;
		}
		Map<String, String> erreurs = new LinkedHashMap<>();
		List<String> regles = RegleMotDePasse.nonRespectees(motDePasse);
		if (!regles.isEmpty()) {
			erreurs.put("motDePasse", ValidationInscription.MOT_DE_PASSE_FAIBLE);
		}
		if (!cguAcceptees) {
			erreurs.put("cgu", CGU_NON_ACCEPTEES);
		}
		if (!erreurs.isEmpty()) {
			ResultatActivation invalide = ResultatActivation.invalide(erreurs, regles);
			EvenementMetier.de(EVENEMENT, invalide.statut()).avec(etat.compte().identifiantsPourLogs())
					.avecChampsInvalides(erreurs).warn();
			return invalide;
		}
		Instant maintenant = horloge.instant();
		Invitation invitation = invitations.findByEmpreinteJeton(Jetons.empreinte(jeton)).orElseThrow();
		invitation.utiliser(maintenant);
		Compte compte = invitation.getCompte();
		compte.activer(encodeur.encode(motDePasse));
		ResultatActivation active = ResultatActivation.de(ResultatActivation.Statut.ACTIVE, compte);
		journaliser(EVENEMENT, active);
		return active;
	}

	private ResultatActivation etat(String jeton) {
		if (jeton == null || jeton.isBlank()) {
			return ResultatActivation.refus(ResultatActivation.Statut.EXPIREE);
		}
		Optional<Invitation> invitation = invitations.findByEmpreinteJeton(Jetons.empreinte(jeton));
		if (invitation.isEmpty()) {
			return ResultatActivation.refus(ResultatActivation.Statut.EXPIREE);
		}
		Compte compte = invitation.get().getCompte();
		if (compte.isActif() || invitation.get().getUtiliseeLe() != null) {
			return ResultatActivation.de(ResultatActivation.Statut.DEJA_ACTIVE, compte);
		}
		if (!invitation.get().estValable(horloge.instant())) {
			return ResultatActivation.de(ResultatActivation.Statut.EXPIREE, compte);
		}
		return ResultatActivation.de(ResultatActivation.Statut.VALABLE, compte);
	}

	// Activé : info ; lien expiré, déjà utilisé : warn.
	private static void journaliser(String event, ResultatActivation resultat) {
		EvenementMetier evenement = EvenementMetier.de(event, resultat.statut());
		if (resultat.compte() != null) {
			evenement.avec(resultat.compte().identifiantsPourLogs());
		}
		evenement.journaliser(resultat.statut() == ResultatActivation.Statut.ACTIVE);
	}

}
