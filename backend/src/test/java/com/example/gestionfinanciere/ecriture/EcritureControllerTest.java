package com.example.gestionfinanciere.ecriture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.ecriture.ResultatEcriture.Statut;

// US-47 Routes du journal : codes HTTP et messages affichés par l'onglet « Écritures ».
class EcritureControllerTest {

	private static final String AUTORISATION = "Bearer jeton";

	private final JournalEcrituresService journal = mock(JournalEcrituresService.class);
	private final SessionService sessions = mock(SessionService.class);
	private final EcritureController controller = new EcritureController(journal, sessions);

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, Instant.EPOCH, Instant.EPOCH);
	private final Compte lucie = Compte.administrateurDe(cabinet, "lucie@test.fr", "x", "Lucie Moreau");
	private final CorrectionEcriture correction = new CorrectionEcriture("EDF",
			List.of(new CorrectionEcriture.LigneCorrigee("6061", BigDecimal.TEN, null)));

	@BeforeEach
	void session() {
		when(sessions.consulter("jeton")).thenReturn(ResultatSession.active(lucie));
		when(sessions.consulter(null)).thenReturn(ResultatSession.nonConnecte());
	}

	@Test
	void consulter() {
		JournalEcritures juin = new JournalEcritures("2026-06", List.of(), List.of(), BigDecimal.ZERO, BigDecimal.ZERO);
		when(journal.consulter(lucie, 42L, "2026-06")).thenReturn(Optional.of(juin));
		when(journal.consulter(eq(lucie), eq(99L), any())).thenReturn(Optional.empty());

		ResponseEntity<Object> reponse = controller.consulter(AUTORISATION, "42", "2026-06");
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(juin, reponse.getBody());
		assertEquals(HttpStatus.NOT_FOUND, controller.consulter(AUTORISATION, "99", null).getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, controller.consulter(AUTORISATION, "abc", null).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.consulter(null, "42", null).getStatusCode());
	}

	@Test
	void modifier() {
		JournalEcritures.Ligne ligne = new JournalEcritures.Ligne(7L, Ecriture.Journal.AC, null, "F-1", "EDF", false,
				Instant.EPOCH, "Lucie Moreau", List.of(), BigDecimal.TEN, BigDecimal.TEN);
		when(journal.modifier(lucie, 42L, 7L, correction)).thenReturn(ResultatEcriture.modifiee(ligne));

		ResponseEntity<Object> reponse = controller.modifier(AUTORISATION, "42", "7", correction);
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(ligne, reponse.getBody());
		assertEquals(HttpStatus.NOT_FOUND, controller.modifier(AUTORISATION, "42", "x", correction).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.modifier(null, "42", "7", correction).getStatusCode());
	}

	@Test
	void refus() {
		when(journal.modifier(lucie, 42L, 7L, correction)).thenReturn(
				ResultatEcriture.refus(Statut.INVALIDE, "Écriture déséquilibrée : écart de 10,00 €"));
		ResponseEntity<Object> desequilibre = controller.modifier(AUTORISATION, "42", "7", correction);
		assertEquals(HttpStatus.BAD_REQUEST, desequilibre.getStatusCode());
		assertEquals(new ErreurResponse("Écriture déséquilibrée : écart de 10,00 €"), desequilibre.getBody());

		when(journal.modifier(lucie, 42L, 7L, correction)).thenReturn(
				ResultatEcriture.refus(Statut.EXPORTEE, JournalEcrituresService.MESSAGE_EXPORTEE));
		assertEquals(HttpStatus.CONFLICT, controller.modifier(AUTORISATION, "42", "7", correction).getStatusCode());

		when(journal.modifier(lucie, 42L, 7L, correction)).thenReturn(ResultatEcriture.de(Statut.ECRITURE_INTROUVABLE));
		ResponseEntity<Object> introuvable = controller.modifier(AUTORISATION, "42", "7", correction);
		assertEquals(HttpStatus.NOT_FOUND, introuvable.getStatusCode());
		assertEquals(new ErreurResponse(EcritureController.MESSAGE_ECRITURE_INTROUVABLE), introuvable.getBody());

		when(journal.modifier(lucie, 42L, 7L, correction)).thenReturn(ResultatEcriture.de(Statut.DOSSIER_INTROUVABLE));
		assertEquals(HttpStatus.NOT_FOUND, controller.modifier(AUTORISATION, "42", "7", correction).getStatusCode());
	}

}
