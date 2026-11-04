package com.example.gestionfinanciere.compte;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// POST /auth/connexion, exposé par le gateway sous /backend/auth/connexion.
//   200 {role, etapeSuivante: "CODE_SMS"}  : identifiants bons
//   401 {message: "Email ou mot de passe incorrect"} : email ou mot de passe faux (RG2)
@RestController
@RequestMapping("/auth")
public class ConnexionController {

	static final String MESSAGE_IDENTIFIANTS_INCORRECTS = "Email ou mot de passe incorrect";

	private final ConnexionService connexion;

	public ConnexionController(ConnexionService connexion) {
		this.connexion = connexion;
	}

	@PostMapping("/connexion")
	public ResponseEntity<Object> seConnecter(@RequestBody ConnexionRequest demande) {
		return connexion.authentifier(demande.email(), demande.motDePasse())
				.<ResponseEntity<Object>>map(role -> ResponseEntity.ok(ConnexionResponse.versCodeSms(role)))
				.orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
						.body(new ErreurResponse(MESSAGE_IDENTIFIANTS_INCORRECTS)));
	}

}
