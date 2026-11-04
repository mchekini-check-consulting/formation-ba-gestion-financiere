package com.example.gestionfinanciere.compte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ConnexionControllerTest {

	private final ConnexionService connexion = mock(ConnexionService.class);
	private final ConnexionController controller = new ConnexionController(connexion);

	@Test
	void ca1ConnexionReussieRenvoieLEtapeCodeSms() {
		when(connexion.authentifier("jean.dupont@cabinet-test.fr", "bon")).thenReturn(Optional.of(Role.COLLABORATEUR));
		ResponseEntity<Object> reponse = controller
				.seConnecter(new ConnexionRequest("jean.dupont@cabinet-test.fr", "bon"));
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(new ConnexionResponse(Role.COLLABORATEUR, "CODE_SMS"), reponse.getBody());
	}

	@Test
	void ca2EchecRenvoie401AvecLeMessageUnique() {
		when(connexion.authentifier("jean.dupont@cabinet-test.fr", "mauvais")).thenReturn(Optional.empty());
		ResponseEntity<Object> reponse = controller
				.seConnecter(new ConnexionRequest("jean.dupont@cabinet-test.fr", "mauvais"));
		assertEquals(HttpStatus.UNAUTHORIZED, reponse.getStatusCode());
		assertEquals(new ErreurResponse("Email ou mot de passe incorrect"), reponse.getBody());
	}

}
