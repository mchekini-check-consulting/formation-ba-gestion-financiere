package com.example.gestionfinanciere.compte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.gestionfinanciere.compte.ConnexionController.SessionOuverte;

class ConnexionControllerTest {

	private final ConnexionService connexion = mock(ConnexionService.class);
	private final SessionService sessions = mock(SessionService.class);
	private final ConnexionController controller = new ConnexionController(connexion, sessions);
	private final Compte jean = new Compte("jean.dupont@cabinet-test.fr", "x", Role.COLLABORATEUR, true,
			"Jean Dupont");

	private ResponseEntity<Object> connexion(String motDePasse) {
		return controller.seConnecter(new ConnexionRequest("jean.dupont@cabinet-test.fr", motDePasse));
	}

	@Test
	void us03Ca1BonMotDePasseOuvreDirectementLaSession() {
		when(connexion.authentifier("jean.dupont@cabinet-test.fr", "bon")).thenReturn(ResultatConnexion.reussie(jean));
		when(sessions.ouvrir(jean)).thenReturn("jeton-session");
		ResponseEntity<Object> reponse = connexion("bon");
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(new SessionOuverte("jeton-session", Role.COLLABORATEUR, "Jean Dupont", false), reponse.getBody());
	}

	@Test
	void us03Ca2EchecRenvoie401AvecLeMessageUniqueSansOuvrirDeSession() {
		when(connexion.authentifier("jean.dupont@cabinet-test.fr", "mauvais"))
				.thenReturn(ResultatConnexion.incorrecte());
		ResponseEntity<Object> reponse = connexion("mauvais");
		assertEquals(HttpStatus.UNAUTHORIZED, reponse.getStatusCode());
		assertEquals(new ErreurResponse("Email ou mot de passe incorrect"), reponse.getBody());
		verify(sessions, never()).ouvrir(jean);
	}

	@Test
	void us05CompteBloqueRenvoie423() {
		when(connexion.authentifier("jean.dupont@cabinet-test.fr", "mauvais")).thenReturn(ResultatConnexion.bloquee());
		ResponseEntity<Object> reponse = connexion("mauvais");
		assertEquals(HttpStatus.LOCKED, reponse.getStatusCode());
		assertEquals(new ErreurResponse(ConnexionController.MESSAGE_COMPTE_BLOQUE), reponse.getBody());
	}

}
