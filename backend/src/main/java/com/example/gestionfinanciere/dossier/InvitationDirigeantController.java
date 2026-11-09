package com.example.gestionfinanciere.dossier;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.SessionController;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.dossier.DossierController.ErreursFormulaire;
import com.example.gestionfinanciere.notification.EnvoiImpossibleException;

// US-14 Inviter le dirigeant, exposé par le gateway sous /backend/dossiers/{id}/invitation ;
// mêmes droits que le dossier (US-11 RG3), session obligatoire (US-07) :
//   POST /dossiers/{id}/invitation         inviter le dirigeant (prénom, nom, email)
//   POST /dossiers/{id}/invitation/renvoi  renvoyer l'invitation (l'ancien lien est annulé)
@RestController
@RequestMapping("/dossiers/{dossierId}/invitation")
public class InvitationDirigeantController {

	static final String MESSAGE_EMAIL_DEJA_UTILISE = "Cette adresse est déjà utilisée";
	static final String MESSAGE_DEJA_INVITE = "Le dirigeant de ce dossier est déjà invité";
	static final String MESSAGE_AUCUNE_INVITATION = "Aucune invitation en attente pour ce dossier";
	static final String MESSAGE_EMAIL_IMPOSSIBLE = "L'email d'invitation n'a pas pu être envoyé. Réessayez.";

	// Réponse d'un envoi : nouvel état de l'espace client ; jetonDeTest en mode test seulement.
	public record InvitationEnvoyee(EtatEspaceClient etatEspaceClient, String jetonDeTest) {
	}

	private final InvitationDirigeantService invitations;
	private final SessionService sessions;

	public InvitationDirigeantController(InvitationDirigeantService invitations, SessionService sessions) {
		this.invitations = invitations;
		this.sessions = sessions;
	}

	@PostMapping
	public ResponseEntity<Object> inviter(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId, @RequestBody DemandeInvitationDirigeant demande) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = DossierController.refus(session);
		if (refus != null) {
			return refus;
		}
		Optional<Long> numero = DossierController.numero(dossierId);
		if (numero.isEmpty()) {
			return DossierController.introuvable();
		}
		try {
			return reponse(invitations.inviter(session.compte(), numero.get(), demande), MESSAGE_DEJA_INVITE,
					HttpStatus.CREATED);
		} catch (EnvoiImpossibleException e) {
			return emailImpossible();
		}
	}

	@PostMapping("/renvoi")
	public ResponseEntity<Object> renvoyer(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = DossierController.refus(session);
		if (refus != null) {
			return refus;
		}
		Optional<Long> numero = DossierController.numero(dossierId);
		if (numero.isEmpty()) {
			return DossierController.introuvable();
		}
		try {
			return reponse(invitations.renvoyer(session.compte(), numero.get()), MESSAGE_AUCUNE_INVITATION,
					HttpStatus.OK);
		} catch (EnvoiImpossibleException e) {
			return emailImpossible();
		}
	}

	private static ResponseEntity<Object> reponse(ResultatInvitationDirigeant resultat, String messageEtat,
			HttpStatus envoyee) {
		return switch (resultat.statut()) {
			case ENVOYEE -> ResponseEntity.status(envoyee)
					.body(new InvitationEnvoyee(EtatEspaceClient.INVITE, resultat.jetonDeTest()));
			case INVALIDE -> ResponseEntity.badRequest()
					.body(new ErreursFormulaire(DossierController.MESSAGE_FORMULAIRE, resultat.erreurs()));
			case INTROUVABLE -> DossierController.introuvable();
			case EMAIL_DEJA_UTILISE -> ResponseEntity.status(HttpStatus.CONFLICT).body(new ErreursFormulaire(
					MESSAGE_EMAIL_DEJA_UTILISE, Map.of("email", MESSAGE_EMAIL_DEJA_UTILISE)));
			case ETAT_INCOMPATIBLE -> ResponseEntity.status(HttpStatus.CONFLICT).body(new ErreurResponse(messageEtat));
		};
	}

	private static ResponseEntity<Object> emailImpossible() {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErreurResponse(MESSAGE_EMAIL_IMPOSSIBLE));
	}

}
