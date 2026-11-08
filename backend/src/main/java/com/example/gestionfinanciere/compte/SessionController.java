package com.example.gestionfinanciere.compte;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// US-07, avec l'en-tête "Authorization: Bearer <jeton>" reçu après le code envoyé par email :
//   GET  /auth/session      session active ? (compte aussi comme activité)
//   POST /auth/deconnexion  « Se déconnecter »
@RestController
@RequestMapping("/auth")
public class SessionController {

	public static final String MESSAGE_SESSION_EXPIREE = "Votre session a expiré";
	public static final String MESSAGE_NON_CONNECTE = "Vous n'êtes pas connecté";
	private static final String PREFIXE = "Bearer ";

	// administrateur : US-19 RG1, l'écran Équipe n'est proposé qu'aux administrateurs du cabinet.
	public record SessionActive(String nomAffiche, Role role, boolean administrateur) {
	}

	// code : SESSION_EXPIREE ou NON_CONNECTE, pour choisir le message de l'écran de connexion.
	public record ErreurSession(String message, String code) {
	}

	private final SessionService sessions;

	public SessionController(SessionService sessions) {
		this.sessions = sessions;
	}

	@GetMapping("/session")
	public ResponseEntity<Object> consulter(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation) {
		ResultatSession resultat = sessions.consulter(jeton(autorisation));
		return switch (resultat.statut()) {
			case ACTIVE -> ResponseEntity.ok(
					new SessionActive(resultat.compte().getNomAffiche(), resultat.compte().getRole(),
								resultat.compte().isAdministrateur()));
			case EXPIREE -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new ErreurSession(MESSAGE_SESSION_EXPIREE, "SESSION_EXPIREE"));
			case NON_CONNECTE -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new ErreurSession(MESSAGE_NON_CONNECTE, "NON_CONNECTE"));
		};
	}

	@PostMapping("/deconnexion")
	public ResponseEntity<Void> seDeconnecter(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation) {
		sessions.fermer(jeton(autorisation));
		return ResponseEntity.noContent().build();
	}

	public static String jeton(String autorisation) {
		if (autorisation == null || !autorisation.startsWith(PREFIXE)) {
			return null;
		}
		return autorisation.substring(PREFIXE.length()).trim();
	}

}
