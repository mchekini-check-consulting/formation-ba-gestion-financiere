package com.example.gestionfinanciere.equipe;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.SessionController;
import com.example.gestionfinanciere.compte.SessionController.ErreurSession;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.notification.EnvoiImpossibleException;

// US-19 Équipe du cabinet, exposée par le gateway sous /backend/equipe/... ; avec l'en-tête
// "Authorization: Bearer <jeton>" de la session (US-07), et réservée aux administrateurs (RG1) :
//   GET  /equipe              collaborateurs du cabinet (actifs et invités)
//   POST /equipe/invitations  inviter un collaborateur avec un rôle
@RestController
@RequestMapping("/equipe")
public class EquipeController {

	static final String MESSAGE_RESERVE = "Seuls les administrateurs du cabinet ont accès à l'équipe";
	static final String MESSAGE_FORMULAIRE = "Certains champs sont à corriger";
	static final String MESSAGE_EMAIL_DEJA_UTILISE = "Cette adresse est déjà utilisée";
	static final String MESSAGE_EMAIL_IMPOSSIBLE = "L'email d'invitation n'a pas pu être envoyé. Réessayez.";

	public record ErreursFormulaire(String message, Map<String, String> erreurs) {
	}

	public record InvitationEnvoyee(Membre membre, String jetonDeTest) {
	}

	private final EquipeService equipe;
	private final SessionService sessions;

	public EquipeController(EquipeService equipe, SessionService sessions) {
		this.equipe = equipe;
		this.sessions = sessions;
	}

	@GetMapping
	public ResponseEntity<Object> membres(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = refus(session);
		if (refus != null) {
			return refus;
		}
		return ResponseEntity.ok(equipe.membres(session.compte()));
	}

	@PostMapping("/invitations")
	public ResponseEntity<Object> inviter(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@RequestBody DemandeInvitation demande) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = refus(session);
		if (refus != null) {
			return refus;
		}
		try {
			ResultatInvitation resultat = equipe.inviter(session.compte(), demande);
			return switch (resultat.statut()) {
				case ENVOYEE -> ResponseEntity.status(HttpStatus.CREATED)
						.body(new InvitationEnvoyee(resultat.membre(), resultat.jetonDeTest()));
				case INVALIDE -> ResponseEntity.badRequest()
						.body(new ErreursFormulaire(MESSAGE_FORMULAIRE, resultat.erreurs()));
				case EMAIL_DEJA_UTILISE -> ResponseEntity.status(HttpStatus.CONFLICT).body(new ErreursFormulaire(
						MESSAGE_EMAIL_DEJA_UTILISE, Map.of("email", MESSAGE_EMAIL_DEJA_UTILISE)));
			};
		} catch (EnvoiImpossibleException e) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(new ErreurResponse(MESSAGE_EMAIL_IMPOSSIBLE));
		}
	}

	// US-07 : session absente ou expirée -> 401 ; RG1 : pas administrateur d'un cabinet -> 403.
	private static ResponseEntity<Object> refus(ResultatSession session) {
		return switch (session.statut()) {
			case NON_CONNECTE -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new ErreurSession(SessionController.MESSAGE_NON_CONNECTE, "NON_CONNECTE"));
			case EXPIREE -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new ErreurSession(SessionController.MESSAGE_SESSION_EXPIREE, "SESSION_EXPIREE"));
			case ACTIVE -> estAdministrateur(session.compte()) ? null : interdit(session.compte());
		};
	}

	// PTO-84 : refus d'accès journalisé (identifiants techniques seulement).
	private static ResponseEntity<Object> interdit(Compte compte) {
		EvenementMetier.de("equipe.acces", "refuse").depuisLeControleur().avec(compte.identifiantsPourLogs()).warn();
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErreurResponse(MESSAGE_RESERVE));
	}

	private static boolean estAdministrateur(Compte compte) {
		return compte.getCabinet() != null && compte.isAdministrateur();
	}

}
