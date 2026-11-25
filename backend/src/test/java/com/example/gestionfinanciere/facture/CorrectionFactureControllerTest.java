package com.example.gestionfinanciere.facture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.compte.SessionService;

// US-55 : routes « informations » et « validation » d'une facture.
class CorrectionFactureControllerTest {

	private static final String AUTORISATION = "Bearer jeton";
	private static final ErreurResponse INTROUVABLE = new ErreurResponse("Facture introuvable ou accès non autorisé");

	private final CorrectionFactureService corrections = mock(CorrectionFactureService.class);
	private final SessionService sessions = mock(SessionService.class);
	private final CorrectionFactureController controller = new CorrectionFactureController(corrections, sessions);

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, Instant.EPOCH, Instant.EPOCH);
	private final Compte lucie = Compte.administrateurDe(cabinet, "lucie@test.fr", "x", "Lucie Bernard");
	private final InformationsFacture informations = new InformationsFacture("FOURNISSEUR", "EDF", "F-1", null, null,
			null, null, null, null);
	private final VueFacture vue = new VueFacture(5L, "edf.pdf", "application/pdf", Facture.Sens.ACHAT,
			Facture.Statut.TRAITEE, null, null);

	private void connecte(Compte compte) {
		when(sessions.consulter("jeton")).thenReturn(ResultatSession.active(compte));
	}

	@Test
	void enregistrerEtValider() {
		connecte(lucie);
		when(corrections.enregistrer(lucie, 42L, 5L, informations))
				.thenReturn(new ResultatCorrection(ResultatCorrection.Issue.ENREGISTREE, null, vue));
		when(corrections.valider(lucie, 42L, 5L, informations))
				.thenReturn(new ResultatCorrection(ResultatCorrection.Issue.VALIDEE, null, vue));

		ResponseEntity<Object> enregistree = controller.enregistrer(AUTORISATION, "42", "5", informations);
		assertEquals(HttpStatus.OK, enregistree.getStatusCode());
		assertEquals(vue, enregistree.getBody());
		assertEquals(vue, controller.valider(AUTORISATION, "42", "5", informations).getBody());
	}

	@Test
	void refus() {
		connecte(lucie);
		when(corrections.valider(lucie, 42L, 5L, informations)).thenReturn(
				ResultatCorrection.refuse(ResultatCorrection.Issue.INVALIDE, "Indiquez le numéro de facture"));
		ResponseEntity<Object> invalide = controller.valider(AUTORISATION, "42", "5", informations);
		assertEquals(HttpStatus.BAD_REQUEST, invalide.getStatusCode());
		assertEquals(new ErreurResponse("Indiquez le numéro de facture"), invalide.getBody());

		when(corrections.enregistrer(lucie, 42L, 5L, informations)).thenReturn(ResultatCorrection
				.refuse(ResultatCorrection.Issue.FIGEE, CorrectionFactureService.MESSAGE_TRAITEE));
		ResponseEntity<Object> figee = controller.enregistrer(AUTORISATION, "42", "5", informations);
		assertEquals(HttpStatus.CONFLICT, figee.getStatusCode());
		assertEquals(new ErreurResponse("Facture traitée : ses informations ne se modifient plus"), figee.getBody());

		when(corrections.enregistrer(lucie, 42L, 6L, informations))
				.thenReturn(ResultatCorrection.refuse(ResultatCorrection.Issue.INTROUVABLE, null));
		ResponseEntity<Object> introuvable = controller.enregistrer(AUTORISATION, "42", "6", informations);
		assertEquals(HttpStatus.NOT_FOUND, introuvable.getStatusCode());
		assertEquals(INTROUVABLE, introuvable.getBody());
	}

	// Adresse tapée à la main, sans connexion, ou dirigeant (réservé au cabinet).
	@Test
	void sessionEtAdresse() {
		connecte(lucie);
		assertEquals(HttpStatus.NOT_FOUND, controller.enregistrer(AUTORISATION, "abc", "5", informations)
				.getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, controller.valider(AUTORISATION, "42", "x", informations).getStatusCode());

		when(sessions.consulter(null)).thenReturn(ResultatSession.nonConnecte());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.valider(null, "42", "5", informations).getStatusCode());
		connecte(new Compte("sophie@test.fr", "x", Role.DIRIGEANT, true, "Sophie Martin"));
		assertEquals(HttpStatus.FORBIDDEN, controller.enregistrer(AUTORISATION, "42", "5", informations)
				.getStatusCode());
		verify(corrections, never()).enregistrer(any(), any(), any(), any());
		verify(corrections, never()).valider(any(), any(), any(), any());
	}

}
