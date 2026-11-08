package com.example.gestionfinanciere.dossier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.cabinet.ValidationInscription;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.compte.RoleCabinet;
import com.example.gestionfinanciere.compte.SessionController.ErreurSession;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.dossier.DossierController.ErreursFormulaire;

class DossierControllerTest {

	private static final String AUTORISATION = "Bearer jeton";

	private final DossierService dossiers = mock(DossierService.class);
	private final SessionService sessions = mock(SessionService.class);
	private final DossierController controller = new DossierController(dossiers, sessions);

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, Instant.EPOCH, Instant.EPOCH);
	// Un comptable (pas administrateur) peut aussi créer un dossier.
	private final Compte jean = Compte.invite(cabinet, "jean@test.fr", "x", "Jean Dupont", RoleCabinet.COMPTABLE);
	private final DemandeDossier demande = DossierServiceTest.fleursMartin();
	private final VueDossier fleurs = new VueDossier(42L, "Fleurs Martin EURL", "812345679", Instant.EPOCH);

	private void connecte(Compte compte) {
		when(sessions.consulter("jeton")).thenReturn(ResultatSession.active(compte));
	}

	@Test
	void ca1DossierCree() {
		connecte(jean);
		when(dossiers.creer(jean, demande)).thenReturn(ResultatCreationDossier.cree(fleurs));
		ResponseEntity<Object> reponse = controller.creer(AUTORISATION, demande);
		assertEquals(HttpStatus.CREATED, reponse.getStatusCode());
		assertEquals(fleurs, reponse.getBody());
	}

	@Test
	void ca2Doublon() {
		connecte(jean);
		when(dossiers.creer(jean, demande)).thenReturn(ResultatCreationDossier.sirenDejaUtilise());
		ResponseEntity<Object> reponse = controller.creer(AUTORISATION, demande);
		assertEquals(HttpStatus.CONFLICT, reponse.getStatusCode());
		assertEquals(new ErreursFormulaire("Un dossier existe déjà pour ce SIREN dans ce cabinet",
				Map.of("siren", "Un dossier existe déjà pour ce SIREN dans ce cabinet")), reponse.getBody());
	}

	@Test
	void formulaireInvalide() {
		connecte(jean);
		when(dossiers.creer(jean, demande))
				.thenReturn(ResultatCreationDossier.invalide(Map.of("siren", ValidationInscription.SIREN_INVALIDE)));
		ResponseEntity<Object> reponse = controller.creer(AUTORISATION, demande);
		assertEquals(HttpStatus.BAD_REQUEST, reponse.getStatusCode());
		assertEquals(new ErreursFormulaire("Certains champs sont à corriger",
				Map.of("siren", ValidationInscription.SIREN_INVALIDE)), reponse.getBody());
	}

	@Test
	void rg3OuvrirLeDossier() {
		connecte(jean);
		when(dossiers.consulter(jean, 42L)).thenReturn(Optional.of(fleurs));
		when(dossiers.consulter(jean, 43L)).thenReturn(Optional.empty());

		ResponseEntity<Object> trouve = controller.consulter(AUTORISATION, 42L);
		assertEquals(HttpStatus.OK, trouve.getStatusCode());
		assertEquals(fleurs, trouve.getBody());

		ResponseEntity<Object> absent = controller.consulter(AUTORISATION, 43L);
		assertEquals(HttpStatus.NOT_FOUND, absent.getStatusCode());
		assertEquals(new ErreurResponse("Dossier introuvable"), absent.getBody());
	}

	@Test
	void reserveAuxCollaborateursDUnCabinet() {
		// Compte sans cabinet (comptes de démonstration).
		connecte(new Compte("demo@test.fr", "x", Role.COLLABORATEUR, true, "Démo", "06"));
		assertEquals(HttpStatus.FORBIDDEN, controller.creer(AUTORISATION, demande).getStatusCode());

		// Dirigeant client.
		connecte(new Compte("sophie@test.fr", "x", Role.DIRIGEANT, true, "Sophie Martin", "06"));
		ResponseEntity<Object> reponse = controller.consulter(AUTORISATION, 42L);
		assertEquals(HttpStatus.FORBIDDEN, reponse.getStatusCode());
		assertEquals(new ErreurResponse(DossierController.MESSAGE_RESERVE), reponse.getBody());
		verify(dossiers, never()).creer(any(), any());
		verify(dossiers, never()).consulter(any(), any());
	}

	@Test
	void sansSessionOuSessionExpiree() {
		when(sessions.consulter(null)).thenReturn(ResultatSession.nonConnecte());
		ResponseEntity<Object> sans = controller.creer(null, demande);
		assertEquals(HttpStatus.UNAUTHORIZED, sans.getStatusCode());
		assertEquals(new ErreurSession("Vous n'êtes pas connecté", "NON_CONNECTE"), sans.getBody());

		when(sessions.consulter("jeton")).thenReturn(ResultatSession.expiree());
		ResponseEntity<Object> expiree = controller.consulter(AUTORISATION, 42L);
		assertEquals(HttpStatus.UNAUTHORIZED, expiree.getStatusCode());
		assertEquals(new ErreurSession("Votre session a expiré", "SESSION_EXPIREE"), expiree.getBody());
	}

}
