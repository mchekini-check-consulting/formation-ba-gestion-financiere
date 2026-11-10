package com.example.gestionfinanciere.compte;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// US-06 Réinitialiser son mot de passe :
//   POST /auth/mot-de-passe-oublie        envoi du lien (même réponse que le compte existe ou non)
//   POST /auth/reinitialisation/verifier  le lien ouvert est-il encore valable ?
//   POST /auth/reinitialisation           nouveau mot de passe
// Le jeton passe dans le corps de la requête, jamais dans l'adresse appelée.
@RestController
@RequestMapping("/auth")
public class MotDePasseController {

	static final String MESSAGE_DEMANDE = "Si un compte existe, un email vous a été envoyé";
	static final String MESSAGE_LIEN_EXPIRE = "Ce lien a expiré, demandez-en un nouveau";
	static final String MESSAGE_TROP_FAIBLE = "Le mot de passe ne respecte pas toutes les règles";

	public record DemandeRequest(String email) {
	}

	public record DemandeEnvoyee(String message) {
	}

	public record LienRequest(String jeton) {
	}

	public record NouveauMotDePasseRequest(String jeton, String motDePasse) {
	}

	public record ErreurMotDePasse(String message, List<String> reglesNonRespectees) {
	}

	private final ReinitialisationService reinitialisation;

	public MotDePasseController(ReinitialisationService reinitialisation) {
		this.reinitialisation = reinitialisation;
	}

	@PostMapping("/mot-de-passe-oublie")
	public DemandeEnvoyee demander(@RequestBody DemandeRequest demande) {
		reinitialisation.demander(demande.email());
		return new DemandeEnvoyee(MESSAGE_DEMANDE);
	}

	@PostMapping("/reinitialisation/verifier")
	public ResponseEntity<Object> verifierLien(@RequestBody LienRequest demande) {
		if (reinitialisation.lienValable(demande.jeton())) {
			return ResponseEntity.noContent().build();
		}
		return lienExpire();
	}

	@PostMapping("/reinitialisation")
	public ResponseEntity<Object> reinitialiser(@RequestBody NouveauMotDePasseRequest demande) {
		ResultatReinitialisation resultat = reinitialisation.reinitialiser(demande.jeton(), demande.motDePasse());
		return switch (resultat.statut()) {
			case CHANGE -> ResponseEntity.noContent().build();
			case LIEN_EXPIRE -> lienExpire();
			case TROP_FAIBLE -> ResponseEntity.badRequest()
					.body(new ErreurMotDePasse(MESSAGE_TROP_FAIBLE, resultat.reglesNonRespectees()));
		};
	}

	private static ResponseEntity<Object> lienExpire() {
		return ResponseEntity.status(HttpStatus.GONE).body(new ErreurResponse(MESSAGE_LIEN_EXPIRE));
	}

}
