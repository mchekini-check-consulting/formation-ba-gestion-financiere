package com.example.gestionfinanciere.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
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
import com.example.gestionfinanciere.compte.SessionService;

class ModuleControllerTest {

	private static final String AUTORISATION = "Bearer jeton";
	private static final ErreurResponse INTROUVABLE = new ErreurResponse("Dossier introuvable ou accès non autorisé");

	private final ModuleService modules = mock(ModuleService.class);
	private final SessionService sessions = mock(SessionService.class);
	private final ModuleController controller = new ModuleController(modules, sessions);

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, Instant.EPOCH, Instant.EPOCH);
	private final Compte jean = Compte.administrateurDe(cabinet, "jean@test.fr", "x", "Jean Dupont");

	// CA1 : modules du dossier.
	@Test
	void ca1Modules() {
		when(sessions.consulter("jeton")).thenReturn(ResultatSession.active(jean));
		List<VueModule> liste = List
				.of(new VueModule("CRM", "CRM", null, ModuleDossier.Statut.BIENTOT_DISPONIBLE));
		when(modules.lister(jean, 42L)).thenReturn(Optional.of(liste));

		ResponseEntity<Object> reponse = controller.lister(AUTORISATION, "42");
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(liste, reponse.getBody());
	}

	// US-11 RG3 : dossier d'un autre cabinet ou adresse tapée à la main.
	@Test
	void dossierIntrouvable() {
		when(sessions.consulter("jeton")).thenReturn(ResultatSession.active(jean));
		when(modules.lister(jean, 43L)).thenReturn(Optional.empty());
		for (String id : List.of("43", "abc")) {
			ResponseEntity<Object> reponse = controller.lister(AUTORISATION, id);
			assertEquals(HttpStatus.NOT_FOUND, reponse.getStatusCode());
			assertEquals(INTROUVABLE, reponse.getBody());
		}
	}

	// US-07 : sans connexion -> 401.
	@Test
	void nonConnecte() {
		when(sessions.consulter(null)).thenReturn(ResultatSession.nonConnecte());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.lister(null, "42").getStatusCode());
	}

}
