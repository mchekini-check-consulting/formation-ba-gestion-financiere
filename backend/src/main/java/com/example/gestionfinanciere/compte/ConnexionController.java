package com.example.gestionfinanciere.compte;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Connexion en une étape, exposée par le gateway sous /backend/auth/... :
//   POST /auth/connexion       email + mot de passe (US-03, blocage US-05) -> ouverture de la session (US-07)
// Le code reçu par email (US-04) a été retiré : la session s'ouvre dès le bon mot de passe.
@RestController
@RequestMapping("/auth")
public class ConnexionController {

	static final String MESSAGE_IDENTIFIANTS_INCORRECTS = "Email ou mot de passe incorrect";
	static final String MESSAGE_COMPTE_BLOQUE = "Compte bloqué 15 minutes : trop de mots de passe incorrects. "
			+ "Un email vous a été envoyé pour changer votre mot de passe.";

	public record SessionOuverte(String jeton, Role role, String nomAffiche, boolean administrateur) {
	}

	private final ConnexionService connexion;
	private final SessionService sessions;

	public ConnexionController(ConnexionService connexion, SessionService sessions) {
		this.connexion = connexion;
		this.sessions = sessions;
	}

	@PostMapping("/connexion")
	public ResponseEntity<Object> seConnecter(@RequestBody ConnexionRequest demande) {
		ResultatConnexion resultat = connexion.authentifier(demande.email(), demande.motDePasse());
		return switch (resultat.statut()) {
			case BLOQUEE -> erreur(HttpStatus.LOCKED, MESSAGE_COMPTE_BLOQUE);
			case INCORRECTE -> erreur(HttpStatus.UNAUTHORIZED, MESSAGE_IDENTIFIANTS_INCORRECTS);
			case REUSSIE -> ouvrirSession(resultat.compte());
		};
	}

	private ResponseEntity<Object> ouvrirSession(Compte compte) {
		String jeton = sessions.ouvrir(compte);
		return ResponseEntity.ok(
				new SessionOuverte(jeton, compte.getRole(), compte.getNomAffiche(), compte.isAdministrateur()));
	}

	private static ResponseEntity<Object> erreur(HttpStatus statut, String message) {
		return ResponseEntity.status(statut).body(new ErreurResponse(message));
	}

}
