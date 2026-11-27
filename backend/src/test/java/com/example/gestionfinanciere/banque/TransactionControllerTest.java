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
import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.SessionService;

class TransactionControllerTest {

	private static final String AUTORISATION = "Bearer jeton";

	private final TransactionService transactions = mock(TransactionService.class);
	private final ValidationOperationService validations = mock(ValidationOperationService.class);
	private final SessionService sessions = mock(SessionService.class);
	private final TransactionController controller = new TransactionController(transactions, validations, sessions);

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, Instant.EPOCH, Instant.EPOCH);
	private final Compte jean = Compte.administrateurDe(cabinet, "jean@test.fr", "x", "Jean Dupont");
	private final VueTransaction edf = new VueTransaction("bouchon-22", LocalDate.of(2026, 11, 16), "PRLV SEPA EDF",
			"EDF", new BigDecimal("186.40"), Transaction.Sens.SORTIE, Transaction.Source.BOUCHON, null);
	private final DemandeValidation achat = new DemandeValidation("ACHAT", "607", new BigDecimal("20"));

	@Test
	void transactionsDuDossier() {
		when(sessions.consulter("jeton")).thenReturn(ResultatSession.active(jean));
		when(transactions.lister(jean, 42L)).thenReturn(Optional.of(List.of(edf)));

		ResponseEntity<Object> reponse = controller.lister(AUTORISATION, "42");
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(List.of(edf), reponse.getBody());
	}

	// US-11 RG3 : dossier inexistant, d'un autre cabinet ou adresse tapée à la main -> 404.
	@Test
	void dossierIntrouvable() {
		when(sessions.consulter("jeton")).thenReturn(ResultatSession.active(jean));
		when(transactions.lister(jean, 99L)).thenReturn(Optional.empty());

		assertEquals(HttpStatus.NOT_FOUND, controller.lister(AUTORISATION, "99").getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, controller.lister(AUTORISATION, "abc").getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, controller.valider(AUTORISATION, "abc", "bouchon-22", achat).getStatusCode());
	}

	// US-07 : sans connexion -> 401.
	@Test
	void nonConnecte() {
		when(sessions.consulter(null)).thenReturn(ResultatSession.nonConnecte());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.lister(null, "42").getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.valider(null, "42", "bouchon-22", achat).getStatusCode());
	}

	// US-30 : validée -> 200 et la ligne à jour ; refus -> 400, 404 ou 409 avec le message.
	@Test
	void us30Validation() {
		when(sessions.consulter("jeton")).thenReturn(ResultatSession.active(jean));
		when(validations.valider(jean, 42L, "bouchon-22", achat))
				.thenReturn(new ResultatValidation(ResultatValidation.Issue.VALIDEE, null, edf));
		ResponseEntity<Object> validee = controller.valider(AUTORISATION, "42", "bouchon-22", achat);
		assertEquals(HttpStatus.OK, validee.getStatusCode());
		assertEquals(edf, validee.getBody());

		when(validations.valider(jean, 42L, "bouchon-23", achat))
				.thenReturn(ResultatValidation.refuse(ResultatValidation.Issue.INVALIDE, "Taux de TVA : entre 0 et 100 %"));
		ResponseEntity<Object> invalide = controller.valider(AUTORISATION, "42", "bouchon-23", achat);
		assertEquals(HttpStatus.BAD_REQUEST, invalide.getStatusCode());
		assertEquals(new ErreurResponse("Taux de TVA : entre 0 et 100 %"), invalide.getBody());

		when(validations.valider(jean, 42L, "bouchon-24", achat))
				.thenReturn(ResultatValidation.refuse(ResultatValidation.Issue.DEJA_VALIDEE, "Opération déjà validée"));
		assertEquals(HttpStatus.CONFLICT, controller.valider(AUTORISATION, "42", "bouchon-24", achat).getStatusCode());

		when(validations.valider(jean, 42L, "inconnue", achat))
				.thenReturn(ResultatValidation.refuse(ResultatValidation.Issue.INTROUVABLE, "Opération introuvable"));
		assertEquals(HttpStatus.NOT_FOUND, controller.valider(AUTORISATION, "42", "inconnue", achat).getStatusCode());
	}

}
