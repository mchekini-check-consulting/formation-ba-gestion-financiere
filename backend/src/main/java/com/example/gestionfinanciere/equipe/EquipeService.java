package com.example.gestionfinanciere.equipe;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.cabinet.ValidationInscription;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.CompteRepository;
import com.example.gestionfinanciere.compte.Jetons;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.Loggable;
import com.example.gestionfinanciere.notification.EnvoiEmail;
import com.example.gestionfinanciere.notification.EnvoiImpossibleException;

// US-19 Inviter un collaborateur avec un rôle. L'appelant vérifie avant que la personne
// connectée est administratrice de son cabinet (RG1).
@Service
@Loggable
public class EquipeService {

	// US-08 RG1 : le lien d'invitation est valable 7 jours.
	static final Duration VALIDITE_INVITATION = Duration.ofDays(7);
	static final String EVENEMENT_INVITATION = "equipe.invitation";

	private final CompteRepository comptes;
	private final InvitationRepository invitations;
	private final PasswordEncoder encodeur;
	private final Clock horloge;
	private final EnvoiEmail emails;
	private final String frontendUrl;

	public EquipeService(CompteRepository comptes, InvitationRepository invitations, PasswordEncoder encodeur,
			Clock horloge, EnvoiEmail emails, @Value("${pointeo.frontend-url}") String frontendUrl) {
		this.comptes = comptes;
		this.invitations = invitations;
		this.encodeur = encodeur;
		this.horloge = horloge;
		this.emails = emails;
		this.frontendUrl = frontendUrl;
	}

	// Collaborateurs du cabinet de l'administrateur, actifs ou invités.
	@Transactional(readOnly = true)
	public List<Membre> membres(Compte administrateur) {
		return comptes.findByCabinetIdOrderByNomAfficheAsc(administrateur.getCabinet().getId()).stream()
				.map(Membre::de).toList();
	}

	// CA1 : crée le compte invité (« Invitée ») et envoie l'email d'invitation.
	@Transactional
	public ResultatInvitation inviter(Compte administrateur, DemandeInvitation demande) {
		try {
			ResultatInvitation resultat = inviterSansJournal(administrateur, demande);
			EvenementMetier evenement = EvenementMetier.de(EVENEMENT_INVITATION, resultat.statut())
					.avec(administrateur.identifiantsPourLogs()).avecChampsInvalides(resultat.erreurs());
			if (resultat.membre() != null) {
				evenement.avec("inviteId", resultat.membre().id()).avec("roleInvite", resultat.membre().role());
			}
			evenement.journaliser(resultat.statut() == ResultatInvitation.Statut.ENVOYEE);
			return resultat;
		} catch (EnvoiImpossibleException e) {
			EvenementMetier.de(EVENEMENT_INVITATION, "email_impossible").avec(administrateur.identifiantsPourLogs())
					.error();
			throw e;
		}
	}

	private ResultatInvitation inviterSansJournal(Compte administrateur, DemandeInvitation demande) {
		Map<String, String> erreurs = ValidationInvitation.erreurs(demande);
		if (!erreurs.isEmpty()) {
			return ResultatInvitation.invalide(erreurs);
		}
		String email = ValidationInscription.email(demande.email());
		if (comptes.findByEmail(email).isPresent()) {
			return ResultatInvitation.emailDejaUtilise(); // RG3, CA2
		}
		String nomAffiche = demande.prenom().trim() + " " + demande.nom().trim();
		Compte invite = comptes.save(Compte.invite(administrateur.getCabinet(), email,
				encodeur.encode(Jetons.nouveau()), nomAffiche, ValidationInvitation.role(demande.role())));
		String jeton = Jetons.nouveau();
		Instant maintenant = horloge.instant();
		invitations.save(new Invitation(invite, administrateur, Jetons.empreinte(jeton), maintenant,
				maintenant.plus(VALIDITE_INVITATION)));
		envoyerEmail(administrateur, invite, jeton);
		return ResultatInvitation.envoyee(Membre.de(invite));
	}

	private void envoyerEmail(Compte administrateur, Compte invite, String jeton) {
		String cabinet = administrateur.getCabinet().getNom();
		emails.envoyer(invite.getEmail(), administrateur.getNomAffiche() + " vous invite à rejoindre " + cabinet
				+ " sur Pointéo",
				"Bonjour " + invite.getNomAffiche() + ",\n\n"
						+ administrateur.getNomAffiche() + " vous invite à rejoindre l'équipe de " + cabinet
						+ " sur Pointéo.\n"
						+ "Pour activer votre compte et choisir votre mot de passe, ouvrez ce lien "
						+ "(valable 7 jours, une seule fois) :\n"
						+ frontendUrl + "/activer-compte?jeton=" + jeton + "\n");
	}

}
