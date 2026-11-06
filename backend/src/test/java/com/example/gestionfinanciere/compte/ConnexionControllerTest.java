package com.example.gestionfinanciere.compte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.gestionfinanciere.compte.ConnexionController.CodeRenvoye;
import com.example.gestionfinanciere.compte.ConnexionController.CodeRequest;
import com.example.gestionfinanciere.compte.ConnexionController.ErreurCode;
import com.example.gestionfinanciere.compte.ConnexionController.RenvoiRequest;
import com.example.gestionfinanciere.compte.ConnexionController.RenvoiTropTot;
import com.example.gestionfinanciere.compte.ConnexionController.SessionOuverte;
import com.example.gestionfinanciere.notification.EnvoiImpossibleException;

class ConnexionControllerTest {

	private final ConnexionService connexion = mock(ConnexionService.class);
	private final CodeSmsService codes = mock(CodeSmsService.class);
	private final SessionService sessions = mock(SessionService.class);
	private final ConnexionController controller = new ConnexionController(connexion, codes, sessions);
	private final Compte jean = new Compte("jean.dupont@cabinet-test.fr", "x", Role.COLLABORATEUR, true,
			"Jean Dupont", "0612345642");
	private final DefiSms defi = new DefiSms("defi", "06 •• •• •• 42", "482917");

	private ResponseEntity<Object> connexion(String motDePasse) {
		return controller.seConnecter(new ConnexionRequest("jean.dupont@cabinet-test.fr", motDePasse));
	}

	@Test
	void us03Ca1ConnexionReussieEnvoieLeCodeSms() {
		when(connexion.authentifier("jean.dupont@cabinet-test.fr", "bon")).thenReturn(ResultatConnexion.reussie(jean));
		when(codes.demarrer(jean)).thenReturn(defi);
		ResponseEntity<Object> reponse = connexion("bon");
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(new ConnexionResponse("CODE_SMS", "defi", "06 •• •• •• 42", "482917"), reponse.getBody());
	}

	@Test
	void us03Ca2EchecRenvoie401AvecLeMessageUnique() {
		when(connexion.authentifier("jean.dupont@cabinet-test.fr", "mauvais"))
				.thenReturn(ResultatConnexion.incorrecte());
		ResponseEntity<Object> reponse = connexion("mauvais");
		assertEquals(HttpStatus.UNAUTHORIZED, reponse.getStatusCode());
		assertEquals(new ErreurResponse("Email ou mot de passe incorrect"), reponse.getBody());
	}

	@Test
	void us05CompteBloqueRenvoie423() {
		when(connexion.authentifier("jean.dupont@cabinet-test.fr", "mauvais")).thenReturn(ResultatConnexion.bloquee());
		ResponseEntity<Object> reponse = connexion("mauvais");
		assertEquals(HttpStatus.LOCKED, reponse.getStatusCode());
		assertEquals(new ErreurResponse(ConnexionController.MESSAGE_COMPTE_BLOQUE), reponse.getBody());
	}

	@Test
	void us04SmsImpossibleRenvoie503() {
		when(connexion.authentifier("jean.dupont@cabinet-test.fr", "bon")).thenReturn(ResultatConnexion.reussie(jean));
		when(codes.demarrer(jean)).thenThrow(new EnvoiImpossibleException("opérateur", null));
		ResponseEntity<Object> reponse = connexion("bon");
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, reponse.getStatusCode());
		assertEquals(new ErreurResponse("Le SMS n'a pas pu être envoyé. Réessayez."), reponse.getBody());
	}

	@Test
	void us04Ca1BonCodeOuvreLaSession() {
		when(codes.verifier("defi", "482917")).thenReturn(ResultatCode.reussi(jean));
		when(sessions.ouvrir(jean)).thenReturn("jeton-session");
		ResponseEntity<Object> reponse = controller.validerCode(new CodeRequest("defi", "482917"));
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(new SessionOuverte("jeton-session", Role.COLLABORATEUR, "Jean Dupont", false), reponse.getBody());
	}

	@Test
	void us04Ca2CodeFaux() {
		when(codes.verifier("defi", "111111")).thenReturn(ResultatCode.faux(2));
		ResponseEntity<Object> reponse = controller.validerCode(new CodeRequest("defi", "111111"));
		assertEquals(HttpStatus.UNAUTHORIZED, reponse.getStatusCode());
		assertEquals(new ErreurCode("Code incorrect. Il vous reste 2 tentatives.", 2, false), reponse.getBody());
		assertEquals("Code incorrect. Il vous reste 1 tentative.", ConnexionController.messageCodeFaux(1));
	}

	@Test
	void us04Rg3TropDeCodesFauxRetourALaConnexion() {
		when(codes.verifier("defi", "111111")).thenReturn(ResultatCode.tropDeCodesFaux());
		ResponseEntity<Object> reponse = controller.validerCode(new CodeRequest("defi", "111111"));
		assertEquals(HttpStatus.UNAUTHORIZED, reponse.getStatusCode());
		assertEquals(new ErreurCode("Trop de codes incorrects. Reconnectez-vous.", 0, true), reponse.getBody());
	}

	@Test
	void us04Ca3CodeExpire() {
		when(codes.verifier("defi", "482917")).thenReturn(ResultatCode.expire(3));
		ResponseEntity<Object> reponse = controller.validerCode(new CodeRequest("defi", "482917"));
		assertEquals(HttpStatus.GONE, reponse.getStatusCode());
		assertEquals(new ErreurCode("Ce code a expiré. Demandez un nouveau code.", 3, false), reponse.getBody());
	}

	@Test
	void us04DemandeInvalide() {
		when(codes.verifier("inconnu", "482917")).thenReturn(ResultatCode.demandeInvalide());
		ResponseEntity<Object> reponse = controller.validerCode(new CodeRequest("inconnu", "482917"));
		assertEquals(HttpStatus.UNAUTHORIZED, reponse.getStatusCode());
		assertEquals(new ErreurCode(ConnexionController.MESSAGE_DEMANDE_INVALIDE, 0, true), reponse.getBody());
	}

	@Test
	void us04Rg2Renvoi() {
		when(codes.renvoyer("defi")).thenReturn(ResultatRenvoi.renvoye("06 •• •• •• 42", "123456"));
		ResponseEntity<Object> reponse = controller.renvoyerCode(new RenvoiRequest("defi"));
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(new CodeRenvoye("06 •• •• •• 42", "123456"), reponse.getBody());
	}

	@Test
	void us04Rg2RenvoiTropTot() {
		when(codes.renvoyer("defi")).thenReturn(ResultatRenvoi.tropTot(40));
		ResponseEntity<Object> reponse = controller.renvoyerCode(new RenvoiRequest("defi"));
		assertEquals(HttpStatus.TOO_MANY_REQUESTS, reponse.getStatusCode());
		assertEquals(new RenvoiTropTot("Vous pourrez demander un nouveau code dans 40 s.", 40), reponse.getBody());
	}

	@Test
	void us04RenvoiDemandeInvalideOuSmsImpossible() {
		when(codes.renvoyer("inconnu")).thenReturn(ResultatRenvoi.demandeInvalide());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.renvoyerCode(new RenvoiRequest("inconnu")).getStatusCode());
		when(codes.renvoyer("defi")).thenThrow(new EnvoiImpossibleException("opérateur", null));
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
				controller.renvoyerCode(new RenvoiRequest("defi")).getStatusCode());
	}

}
