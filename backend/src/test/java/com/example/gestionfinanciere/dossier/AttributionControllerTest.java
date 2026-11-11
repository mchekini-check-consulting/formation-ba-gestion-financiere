package com.example.gestionfinanciere.dossier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.compte.SessionService;

class AttributionControllerTest {

	private static final String AUTORISATION = "Bearer jeton";
	private static final ErreurResponse INTROUVABLE = new ErreurResponse("Dossier introuvable ou accès non autorisé");

	private final AttributionService attributions = mock(AttributionService.class);
	private final SessionService sessions = mock(SessionService.class);
	private final AttributionController controller = new AttributionController(attributions, sessions);

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, Instant.EPOCH, Instant.EPOCH);
	private final Compte jean = Compte.administrateurDe(cabinet, "jean@test.fr", "x", "Jean Dupont");
	private final ComptablesDossier vue = new ComptablesDossier(
			List.of(new ComptablesDossier.Comptable(5L, "Lucie Bernard", true)), true);
	private final DemandeAttribution demande = new DemandeAttribution(List.of(5L));

	private void connecte(Compte compte) {
		when(sessions.consulter("jeton")).thenReturn(ResultatSession.active(compte));
	}

	@Test
	void consulterLesComptablesDuDossier() {
		connecte(jean);
		when(attributions.consulter(jean, 42L)).thenReturn(Optional.of(vue));
		when(attributions.consulter(jean, 43L)).thenReturn(Optional.empty());

		ResponseEntity<Object> reponse = controller.consulter(AUTORISATION, "42");
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(vue, reponse.getBody());
		for (String id : List.of("43", "abc")) {
			ResponseEntity<Object> refus = controller.consulter(AUTORISATION, id);
			assertEquals(HttpStatus.NOT_FOUND, refus.getStatusCode());
			assertEquals(INTROUVABLE, refus.getBody());
		}
	}

	// CA1 : attribution enregistrée ; refus selon le rôle (RG1) et les droits (RG2).
	@Test
	void attribuer() {
		connecte(jean);
		when(attributions.attribuer(jean, 42L, demande)).thenReturn(ResultatAttribution.attribue(vue));
		ResponseEntity<Object> reponse = controller.attribuer(AUTORISATION, "42", demande);
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(vue, reponse.getBody());

		when(attributions.attribuer(jean, 42L, demande))
				.thenReturn(ResultatAttribution.refus(ResultatAttribution.Statut.INTERDIT));
		ResponseEntity<Object> interdit = controller.attribuer(AUTORISATION, "42", demande);
		assertEquals(HttpStatus.FORBIDDEN, interdit.getStatusCode());
		assertEquals(new ErreurResponse(AttributionController.MESSAGE_INTERDIT), interdit.getBody());

		when(attributions.attribuer(jean, 42L, demande))
				.thenReturn(ResultatAttribution.refus(ResultatAttribution.Statut.INTROUVABLE));
		assertEquals(HttpStatus.NOT_FOUND, controller.attribuer(AUTORISATION, "42", demande).getStatusCode());

		when(attributions.attribuer(jean, 42L, demande))
				.thenReturn(ResultatAttribution.refus(ResultatAttribution.Statut.INVALIDE));
		ResponseEntity<Object> invalide = controller.attribuer(AUTORISATION, "42", demande);
		assertEquals(HttpStatus.BAD_REQUEST, invalide.getStatusCode());
		assertEquals(new ErreurResponse(AttributionController.MESSAGE_INVALIDE), invalide.getBody());

		assertEquals(HttpStatus.NOT_FOUND, controller.attribuer(AUTORISATION, "abc", demande).getStatusCode());
	}

	@Test
	void sessionEtRole() {
		when(sessions.consulter(null)).thenReturn(ResultatSession.nonConnecte());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.consulter(null, "42").getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.attribuer(null, "42", demande).getStatusCode());
		connecte(new Compte("sophie@test.fr", "x", Role.DIRIGEANT, true, "Sophie Martin"));
		assertEquals(HttpStatus.FORBIDDEN, controller.consulter(AUTORISATION, "42").getStatusCode());
		assertEquals(HttpStatus.FORBIDDEN, controller.attribuer(AUTORISATION, "42", demande).getStatusCode());
		verify(attributions, never()).consulter(any(), any());
		verify(attributions, never()).attribuer(any(), any(), any());
	}

}
