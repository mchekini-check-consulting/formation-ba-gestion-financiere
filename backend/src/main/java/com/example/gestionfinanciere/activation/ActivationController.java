package com.example.gestionfinanciere.activation;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.ConnexionController.SessionOuverte;
import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.compte.SessionService;

// US-08 Activer son compte, depuis le lien d'invitation :
//   POST /auth/activation/verifier  le lien est-il valable ? (nom de la personne et rôle)
//   POST /auth/activation           mot de passe + CGU : compte actif et session ouverte
// Le jeton passe dans le corps de la requête, jamais dans l'adresse appelée.
@RestController
@RequestMapping("/auth/activation")
public class ActivationController {

	static final String MESSAGE_EXPIREE = "Cette invitation a expiré. Demandez à votre cabinet de vous la renvoyer.";
	static final String MESSAGE_DEJA_ACTIVE = "Votre espace est déjà activé";
	static final String MESSAGE_FORMULAIRE = "Certains champs sont à corriger";

	public record LienRequest(String jeton) {
	}

	public record ActivationRequest(String jeton, String motDePasse, boolean cguAcceptees) {
	}

	public record Invite(String nomAffiche, Role role) {
	}

	public record ErreursActivation(String message, Map<String, String> erreurs, List<String> reglesNonRespectees) {
	}

	private final ActivationService activation;
	private final SessionService sessions;

	public ActivationController(ActivationService activation, SessionService sessions) {
		this.activation = activation;
		this.sessions = sessions;
	}

	@PostMapping("/verifier")
	public ResponseEntity<Object> verifier(@RequestBody LienRequest demande) {
		ResultatActivation resultat = activation.verifier(demande.jeton());
		if (resultat.statut() == ResultatActivation.Statut.VALABLE) {
			Compte compte = resultat.compte();
			return ResponseEntity.ok(new Invite(compte.getNomAffiche(), compte.getRole()));
		}
		return refus(resultat);
	}

	@PostMapping
	public ResponseEntity<Object> activer(@RequestBody ActivationRequest demande) {
		ResultatActivation resultat = activation.activer(demande.jeton(), demande.motDePasse(), demande.cguAcceptees());
		return switch (resultat.statut()) {
			case ACTIVE -> {
				Compte compte = resultat.compte();
				yield ResponseEntity.ok(new SessionOuverte(sessions.ouvrir(compte), compte.getRole(),
						compte.getNomAffiche(), compte.isAdministrateur()));
			}
			case INVALIDE -> ResponseEntity.badRequest().body(new ErreursActivation(MESSAGE_FORMULAIRE,
					resultat.erreurs(), resultat.reglesNonRespectees()));
			default -> refus(resultat);
		};
	}

	private static ResponseEntity<Object> refus(ResultatActivation resultat) {
		if (resultat.statut() == ResultatActivation.Statut.DEJA_ACTIVE) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErreurResponse(MESSAGE_DEJA_ACTIVE));
		}
		return ResponseEntity.status(HttpStatus.GONE).body(new ErreurResponse(MESSAGE_EXPIREE));
	}

}
