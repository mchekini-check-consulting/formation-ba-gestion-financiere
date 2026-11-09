package com.example.gestionfinanciere.dossier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.cabinet.ValidationInscription;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.CompteRepository;
import com.example.gestionfinanciere.compte.Jetons;
import com.example.gestionfinanciere.equipe.Invitation;
import com.example.gestionfinanciere.equipe.InvitationRepository;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.Loggable;
import com.example.gestionfinanciere.notification.EnvoiEmail;
import com.example.gestionfinanciere.notification.EnvoiImpossibleException;

/**
 * US-14 Inviter le dirigeant d'un dossier. L'appelant vérifie avant que la personne connectée
 * est un collaborateur d'un cabinet ; ici, le dossier doit être celui de son cabinet.
 * RG2 : lien valable 7 jours ; « Renvoyer l'invitation » annule l'ancien lien. RG3 : l'espace
 * client passe de Non invité à Invité (compte du dirigeant créé inactif), puis Actif (US-08).
 * Seule l'empreinte du jeton est stockée ; ni jeton ni email dans les logs.
 */
@Service
@Loggable
public class InvitationDirigeantService {

	static final Duration VALIDITE_INVITATION = Duration.ofDays(7);
	private static final String EVENEMENT_INVITATION = "dossier.invitation_dirigeant";
	private static final String EVENEMENT_RENVOI = "dossier.renvoi_invitation_dirigeant";
	private static final String CHAMP_DOSSIER = "dossierId";

	private final DossierRepository dossiers;
	private final CompteRepository comptes;
	private final InvitationRepository invitations;
	private final PasswordEncoder encodeur;
	private final Clock horloge;
	private final EnvoiEmail emails;
	private final String frontendUrl;
	private final boolean modeTest;

	public InvitationDirigeantService(DossierRepository dossiers, CompteRepository comptes,
			InvitationRepository invitations, PasswordEncoder encodeur, Clock horloge, EnvoiEmail emails,
			@Value("${pointeo.frontend-url}") String frontendUrl, @Value("${pointeo.mode-test:false}") boolean modeTest) {
		this.dossiers = dossiers;
		this.comptes = comptes;
		this.invitations = invitations;
		this.encodeur = encodeur;
		this.horloge = horloge;
		this.emails = emails;
		this.frontendUrl = frontendUrl;
		this.modeTest = modeTest;
	}

	// RG1 : crée le compte du dirigeant (inactif) et lui envoie l'invitation au nom du cabinet.
	@Transactional
	public ResultatInvitationDirigeant inviter(Compte collaborateur, Long dossierId,
			DemandeInvitationDirigeant demande) {
		return journaliser(EVENEMENT_INVITATION, collaborateur, dossierId, () -> {
			Optional<Dossier> dossier = dossiers.findByIdAndCabinetId(dossierId, collaborateur.getCabinet().getId());
			if (dossier.isEmpty()) {
				return ResultatInvitationDirigeant.refus(ResultatInvitationDirigeant.Statut.INTROUVABLE);
			}
			Map<String, String> erreurs = ValidationInvitationDirigeant.erreurs(demande);
			if (!erreurs.isEmpty()) {
				return ResultatInvitationDirigeant.invalide(erreurs);
			}
			if (comptes.existsByDossierId(dossierId)) {
				return ResultatInvitationDirigeant.refus(ResultatInvitationDirigeant.Statut.ETAT_INCOMPATIBLE);
			}
			String email = ValidationInscription.email(demande.email());
			if (comptes.findByEmail(email).isPresent()) {
				return ResultatInvitationDirigeant.refus(ResultatInvitationDirigeant.Statut.EMAIL_DEJA_UTILISE);
			}
			String nomAffiche = demande.prenom().trim() + " " + demande.nom().trim();
			Compte dirigeant = comptes.save(Compte.dirigeantInvite(dossierId, email, encodeur.encode(Jetons.nouveau()),
					nomAffiche));
			return envoyer(collaborateur, dossier.get(), dirigeant);
		});
	}

	// RG2 : nouveau lien, l'ancien est annulé ; seulement si le dirigeant est invité (pas encore actif).
	@Transactional
	public ResultatInvitationDirigeant renvoyer(Compte collaborateur, Long dossierId) {
		return journaliser(EVENEMENT_RENVOI, collaborateur, dossierId, () -> {
			Optional<Dossier> dossier = dossiers.findByIdAndCabinetId(dossierId, collaborateur.getCabinet().getId());
			if (dossier.isEmpty()) {
				return ResultatInvitationDirigeant.refus(ResultatInvitationDirigeant.Statut.INTROUVABLE);
			}
			Optional<Compte> dirigeant = comptes.findFirstByDossierIdOrderByIdDesc(dossierId)
					.filter(compte -> !compte.isActif());
			if (dirigeant.isEmpty()) {
				return ResultatInvitationDirigeant.refus(ResultatInvitationDirigeant.Statut.ETAT_INCOMPATIBLE);
			}
			Instant maintenant = horloge.instant();
			invitations.findByCompteIdAndUtiliseeLeIsNullAndAnnuleeLeIsNull(dirigeant.get().getId())
					.forEach(ancienne -> ancienne.annuler(maintenant));
			return envoyer(collaborateur, dossier.get(), dirigeant.get());
		});
	}

	private ResultatInvitationDirigeant envoyer(Compte collaborateur, Dossier dossier, Compte dirigeant) {
		String jeton = Jetons.nouveau();
		Instant maintenant = horloge.instant();
		invitations.save(new Invitation(dirigeant, collaborateur, Jetons.empreinte(jeton), maintenant,
				maintenant.plus(VALIDITE_INVITATION)));
		String cabinet = dossier.getCabinet().getNom();
		emails.envoyer(dirigeant.getEmail(), cabinet + " vous invite sur votre espace client Pointéo",
				"Bonjour " + dirigeant.getNomAffiche() + ",\n\n"
						+ "Le cabinet " + cabinet + " vous invite à accéder à l'espace client de "
						+ dossier.getRaisonSociale() + " sur Pointéo, pour déposer et suivre vos factures.\n"
						+ "Pour activer votre espace et choisir votre mot de passe, ouvrez ce lien "
						+ "(valable 7 jours, une seule fois) :\n"
						+ frontendUrl + "/activer-compte?jeton=" + jeton + "\n\n"
						+ cabinet + "\n");
		return ResultatInvitationDirigeant.envoyee(modeTest ? jeton : null);
	}

	private interface Action {
		ResultatInvitationDirigeant executer();
	}

	// Logs : envoyée (info), refusée (warn), email impossible (error) ; identifiants seulement.
	private static ResultatInvitationDirigeant journaliser(String event, Compte collaborateur, Long dossierId,
			Action action) {
		try {
			ResultatInvitationDirigeant resultat = action.executer();
			EvenementMetier.de(event, resultat.statut()).avec(collaborateur.identifiantsPourLogs())
					.avec(CHAMP_DOSSIER, dossierId).avecChampsInvalides(resultat.erreurs())
					.journaliser(resultat.statut() == ResultatInvitationDirigeant.Statut.ENVOYEE);
			return resultat;
		} catch (EnvoiImpossibleException e) {
			EvenementMetier.de(event, "email_impossible").avec(collaborateur.identifiantsPourLogs())
					.avec(CHAMP_DOSSIER, dossierId).error();
			throw e;
		}
	}

}
