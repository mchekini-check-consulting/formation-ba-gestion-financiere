package com.example.gestionfinanciere.compte;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.gestionfinanciere.notification.EnvoiImpossibleException;

// Connexion en deux étapes, exposée par le gateway sous /backend/auth/... :
//   POST /auth/connexion       email + mot de passe (US-03, blocage US-05) -> envoi du code par email
//   POST /auth/code            code reçu par email (US-04) -> ouverture de la session (US-07)
//   POST /auth/code/renvoyer   nouveau code après 60 s (US-04 RG2)
@RestController
@RequestMapping("/auth")
public class ConnexionController {

	static final String MESSAGE_IDENTIFIANTS_INCORRECTS = "Email ou mot de passe incorrect";
	static final String MESSAGE_COMPTE_BLOQUE = "Compte bloqué 15 minutes : trop de mots de passe incorrects. "
			+ "Un email vous a été envoyé pour changer votre mot de passe.";
	static final String MESSAGE_EMAIL_IMPOSSIBLE = "L’email n’a pas pu être envoyé. Réessayez.";
	static final String MESSAGE_DEMANDE_INVALIDE = "Votre demande de connexion n'est plus valable. Reconnectez-vous.";
	static final String MESSAGE_TROP_DE_CODES = "Trop de codes incorrects. Reconnectez-vous.";
	static final String MESSAGE_CODE_EXPIRE = "Ce code a expiré. Demandez un nouveau code.";

	public record CodeRequest(String defi, String code) {
	}

	public record RenvoiRequest(String defi) {
	}

	public record SessionOuverte(String jeton, Role role, String nomAffiche, boolean administrateur) {
	}

	// retourConnexion : l'écran doit revenir à la connexion (US-04 RG3).
	public record ErreurCode(String message, int tentativesRestantes, boolean retourConnexion) {
	}

	public record CodeRenvoye(String codeDeTest) {
	}

	public record RenvoiTropTot(String message, long secondesRestantes) {
	}

	private final ConnexionService connexion;
	private final CodeConnexionService codes;
	private final SessionService sessions;

	public ConnexionController(ConnexionService connexion, CodeConnexionService codes, SessionService sessions) {
		this.connexion = connexion;
		this.codes = codes;
		this.sessions = sessions;
	}

	@PostMapping("/connexion")
	public ResponseEntity<Object> seConnecter(@RequestBody ConnexionRequest demande) {
		ResultatConnexion resultat = connexion.authentifier(demande.email(), demande.motDePasse());
		return switch (resultat.statut()) {
			case BLOQUEE -> erreur(HttpStatus.LOCKED, MESSAGE_COMPTE_BLOQUE);
			case INCORRECTE -> erreur(HttpStatus.UNAUTHORIZED, MESSAGE_IDENTIFIANTS_INCORRECTS);
			case REUSSIE -> envoyerCode(resultat.compte());
		};
	}

	@PostMapping("/code")
	public ResponseEntity<Object> validerCode(@RequestBody CodeRequest demande) {
		ResultatCode resultat = codes.verifier(demande.defi(), demande.code());
		return switch (resultat.statut()) {
			case REUSSI -> ouvrirSession(resultat.compte());
			case FAUX -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new ErreurCode(messageCodeFaux(resultat.tentativesRestantes()),
							resultat.tentativesRestantes(), false));
			case TROP_DE_CODES_FAUX -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new ErreurCode(MESSAGE_TROP_DE_CODES, 0, true));
			case EXPIRE -> ResponseEntity.status(HttpStatus.GONE)
					.body(new ErreurCode(MESSAGE_CODE_EXPIRE, resultat.tentativesRestantes(), false));
			case DEMANDE_INVALIDE -> demandeInvalide();
		};
	}

	@PostMapping("/code/renvoyer")
	public ResponseEntity<Object> renvoyerCode(@RequestBody RenvoiRequest demande) {
		try {
			ResultatRenvoi resultat = codes.renvoyer(demande.defi());
			return switch (resultat.statut()) {
				case RENVOYE -> ResponseEntity.ok(new CodeRenvoye(resultat.codeDeTest()));
				case TROP_TOT -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
						.body(new RenvoiTropTot("Vous pourrez demander un nouveau code dans "
								+ resultat.secondesRestantes() + " s.", resultat.secondesRestantes()));
				case DEMANDE_INVALIDE -> demandeInvalide();
			};
		} catch (EnvoiImpossibleException e) {
			return erreur(HttpStatus.SERVICE_UNAVAILABLE, MESSAGE_EMAIL_IMPOSSIBLE);
		}
	}

	private ResponseEntity<Object> envoyerCode(Compte compte) {
		try {
			return ResponseEntity.ok(ConnexionResponse.versCodeEmail(codes.demarrer(compte)));
		} catch (EnvoiImpossibleException e) {
			return erreur(HttpStatus.SERVICE_UNAVAILABLE, MESSAGE_EMAIL_IMPOSSIBLE);
		}
	}

	private ResponseEntity<Object> ouvrirSession(Compte compte) {
		String jeton = sessions.ouvrir(compte);
		return ResponseEntity.ok(
				new SessionOuverte(jeton, compte.getRole(), compte.getNomAffiche(), compte.isAdministrateur()));
	}

	private static ResponseEntity<Object> demandeInvalide() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErreurCode(MESSAGE_DEMANDE_INVALIDE, 0, true));
	}

	private static ResponseEntity<Object> erreur(HttpStatus statut, String message) {
		return ResponseEntity.status(statut).body(new ErreurResponse(message));
	}

	// US-04 CA2 : « Code incorrect. Il vous reste 2 tentatives. »
	static String messageCodeFaux(int restantes) {
		return "Code incorrect. Il vous reste " + restantes + (restantes > 1 ? " tentatives." : " tentative.");
	}

}
