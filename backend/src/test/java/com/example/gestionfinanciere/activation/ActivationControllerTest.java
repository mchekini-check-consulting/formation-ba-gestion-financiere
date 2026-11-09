package com.example.gestionfinanciere.activation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.gestionfinanciere.activation.ActivationController.ActivationRequest;
import com.example.gestionfinanciere.activation.ActivationController.ErreursActivation;
import com.example.gestionfinanciere.activation.ActivationController.Invite;
import com.example.gestionfinanciere.activation.ActivationController.LienRequest;
import com.example.gestionfinanciere.activation.ResultatActivation.Statut;
import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.ConnexionController.SessionOuverte;
import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.compte.RoleCabinet;
import com.example.gestionfinanciere.compte.SessionService;

class ActivationControllerTest {

	private static final String MOT_DE_PASSE = "Sophie#Pointeo2026";

	private final ActivationService activation = mock(ActivationService.class);
	private final SessionService sessions = mock(SessionService.class);
	private final ActivationController controller = new ActivationController(activation, sessions);

	private final Compte sophie = Compte.dirigeantInvite(42L, "sophie@test.fr", "x", "Sophie Martin");
	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, Instant.EPOCH, Instant.EPOCH);
	private final Compte paul = Compte.invite(cabinet, "paul@test.fr", "x", "Paul Durand", RoleCabinet.COMPTABLE);

	@Test
	void lienValable() {
		when(activation.verifier("j")).thenReturn(ResultatActivation.de(Statut.VALABLE, sophie));
		ResponseEntity<Object> reponse = controller.verifier(new LienRequest("j"));
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(new Invite("Sophie Martin", Role.DIRIGEANT), reponse.getBody());
	}

	@Test
	void lienExpireOuDejaUtilise() {
		when(activation.verifier("expire")).thenReturn(ResultatActivation.refus(Statut.EXPIREE));
		when(activation.verifier("utilise")).thenReturn(ResultatActivation.de(Statut.DEJA_ACTIVE, sophie));
		ResponseEntity<Object> expire = controller.verifier(new LienRequest("expire"));
		assertEquals(HttpStatus.GONE, expire.getStatusCode());
		assertEquals(new ErreurResponse("Cette invitation a expiré. Demandez à votre cabinet de vous la renvoyer."),
				expire.getBody());
		ResponseEntity<Object> utilise = controller.verifier(new LienRequest("utilise"));
		assertEquals(HttpStatus.CONFLICT, utilise.getStatusCode());
		assertEquals(new ErreurResponse("Votre espace est déjà activé"), utilise.getBody());
	}

	@Test
	void rg3ActivationOuvreLaSession() {
		when(activation.activer("j", MOT_DE_PASSE, true)).thenReturn(ResultatActivation.de(Statut.ACTIVE, paul));
		when(sessions.ouvrir(paul)).thenReturn("session");
		ResponseEntity<Object> reponse = controller.activer(new ActivationRequest("j", MOT_DE_PASSE, true));
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(new SessionOuverte("session", Role.COLLABORATEUR, "Paul Durand", false), reponse.getBody());
	}

	@Test
	void formulaireARevoirOuLienRefuse() {
		Map<String, String> erreurs = Map.of("cgu", ActivationService.CGU_NON_ACCEPTEES);
		when(activation.activer("j", MOT_DE_PASSE, false))
				.thenReturn(ResultatActivation.invalide(erreurs, List.of()));
		ResponseEntity<Object> invalide = controller.activer(new ActivationRequest("j", MOT_DE_PASSE, false));
		assertEquals(HttpStatus.BAD_REQUEST, invalide.getStatusCode());
		assertEquals(new ErreursActivation("Certains champs sont à corriger", erreurs, List.of()), invalide.getBody());

		when(activation.activer("vieux", MOT_DE_PASSE, true)).thenReturn(ResultatActivation.refus(Statut.EXPIREE));
		assertEquals(HttpStatus.GONE,
				controller.activer(new ActivationRequest("vieux", MOT_DE_PASSE, true)).getStatusCode());
		verify(sessions, never()).ouvrir(any());
	}

}
