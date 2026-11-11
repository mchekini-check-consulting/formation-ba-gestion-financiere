package com.example.gestionfinanciere.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.SessionService;

class ModuleControllerTest {

	private static final String AUTORISATION = "Bearer jeton";

	private final ModuleService modules = mock(ModuleService.class);
	private final SessionService sessions = mock(SessionService.class);
	private final ModuleController controller = new ModuleController(modules, sessions);

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, Instant.EPOCH, Instant.EPOCH);
	private final Compte jean = Compte.administrateurDe(cabinet, "jean@test.fr", "x", "Jean Dupont");

	// CA1 : modules du menu de gauche.
	@Test
	void ca1Modules() {
		when(sessions.consulter("jeton")).thenReturn(ResultatSession.active(jean));
		List<VueModule> liste = List
				.of(new VueModule("CRM", "CRM", null, ModuleDossier.Statut.BIENTOT_DISPONIBLE));
		when(modules.lister(jean)).thenReturn(liste);

		ResponseEntity<Object> reponse = controller.lister(AUTORISATION);
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(liste, reponse.getBody());
	}

	// US-07 : sans connexion -> 401.
	@Test
	void nonConnecte() {
		when(sessions.consulter(null)).thenReturn(ResultatSession.nonConnecte());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.lister(null).getStatusCode());
	}

}
