package com.example.gestionfinanciere.banque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.SessionService;

class TransactionControllerTest {

	private static final String AUTORISATION = "Bearer jeton";

	private final TransactionService transactions = mock(TransactionService.class);
	private final SessionService sessions = mock(SessionService.class);
	private final TransactionController controller = new TransactionController(transactions, sessions);

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, Instant.EPOCH, Instant.EPOCH);
	private final Compte jean = Compte.administrateurDe(cabinet, "jean@test.fr", "x", "Jean Dupont");

	@Test
	void transactionsDuDossier() {
		when(sessions.consulter("jeton")).thenReturn(ResultatSession.active(jean));
		List<Transaction> liste = List.of(new Transaction(LocalDate.of(2026, 11, 16), "PRLV SEPA EDF", "EDF",
				new BigDecimal("186.40"), Transaction.Sens.SORTIE, Transaction.Source.BOUCHON));
		when(transactions.lister(jean, 42L)).thenReturn(Optional.of(liste));

		ResponseEntity<Object> reponse = controller.lister(AUTORISATION, "42");
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(liste, reponse.getBody());
	}

	// US-11 RG3 : dossier inexistant, d'un autre cabinet ou adresse tapée à la main -> 404.
	@Test
	void dossierIntrouvable() {
		when(sessions.consulter("jeton")).thenReturn(ResultatSession.active(jean));
		when(transactions.lister(jean, 99L)).thenReturn(Optional.empty());

		assertEquals(HttpStatus.NOT_FOUND, controller.lister(AUTORISATION, "99").getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, controller.lister(AUTORISATION, "abc").getStatusCode());
	}

	// US-07 : sans connexion -> 401.
	@Test
	void nonConnecte() {
		when(sessions.consulter(null)).thenReturn(ResultatSession.nonConnecte());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.lister(null, "42").getStatusCode());
	}

}
