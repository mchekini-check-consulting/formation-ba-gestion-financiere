package com.example.gestionfinanciere.facture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.compte.SessionService;

class FicheFactureControllerTest {

	private static final String AUTORISATION = "Bearer jeton";

	private final FicheFactureService fiches = mock(FicheFactureService.class);
	private final SessionService sessions = mock(SessionService.class);
	private final FicheFactureController controller = new FicheFactureController(fiches, sessions);

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, Instant.EPOCH, Instant.EPOCH);
	private final Compte lucie = Compte.administrateurDe(cabinet, "lucie@test.fr", "x", "Lucie Moreau");
	private final InformationsFacture demande = new InformationsFacture("FOURNISSEUR", "EDF", "F-2026-0611",
			LocalDate.of(2026, 6, 12), new BigDecimal("155.33"), new BigDecimal("31.07"), new BigDecimal("20"),
			new BigDecimal("186.40"), "606");
	private final VueFacture vue = new VueFacture(5L, "edf.pdf", "application/pdf", Facture.Sens.ACHAT, null, null);

	private void connecte(Compte compte) {
		when(sessions.consulter("jeton")).thenReturn(ResultatSession.active(compte));
	}

	// RG6 : fiche validée et facture suivante à traiter ; RG2 : fiche enregistrée.
	@Test
	void validationEtEnregistrement() {
		connecte(lucie);
		when(fiches.valider(lucie, 42L, 5L, demande))
				.thenReturn(new ResultatFiche(ResultatFiche.Issue.VALIDEE, null, vue, 6L));
		when(fiches.enregistrer(lucie, 42L, 5L, demande))
				.thenReturn(new ResultatFiche(ResultatFiche.Issue.ENREGISTREE, null, vue, null));

		ResponseEntity<Object> validee = controller.valider(AUTORISATION, "42", "5", demande);
		assertEquals(HttpStatus.OK, validee.getStatusCode());
		assertEquals(new FicheFactureController.ReponseFiche(vue, 6L), validee.getBody());

		ResponseEntity<Object> enregistree = controller.enregistrer(AUTORISATION, "42", "5", demande);
		assertEquals(HttpStatus.OK, enregistree.getStatusCode());
		assertEquals(new FicheFactureController.ReponseFiche(vue, null), enregistree.getBody());
	}

	// CA2, CA3 : 400 avec le message affiché tel quel ; RG8 : 409 ; facture d'un autre dossier : 404.
	@Test
	void refus() {
		connecte(lucie);
		when(fiches.valider(lucie, 42L, 5L, demande))
				.thenReturn(ResultatFiche.refuse("Indiquez le numéro de facture"));
		ResponseEntity<Object> refusee = controller.valider(AUTORISATION, "42", "5", demande);
		assertEquals(HttpStatus.BAD_REQUEST, refusee.getStatusCode());
		assertEquals(new ErreurResponse("Indiquez le numéro de facture"), refusee.getBody());

		when(fiches.enregistrer(lucie, 42L, 5L, demande))
				.thenReturn(ResultatFiche.refuse(ResultatFiche.Issue.NON_MODIFIABLE));
		ResponseEntity<Object> traitee = controller.enregistrer(AUTORISATION, "42", "5", demande);
		assertEquals(HttpStatus.CONFLICT, traitee.getStatusCode());
		assertEquals(new ErreurResponse(
				"Cette facture n'est plus modifiable : elle est déjà traitée ou en cours de lecture"),
				traitee.getBody());

		when(fiches.valider(lucie, 42L, 7L, demande)).thenReturn(ResultatFiche.refuse(ResultatFiche.Issue.INTROUVABLE));
		ResponseEntity<Object> introuvable = controller.valider(AUTORISATION, "42", "7", demande);
		assertEquals(HttpStatus.NOT_FOUND, introuvable.getStatusCode());
		assertEquals(new ErreurResponse("Facture introuvable ou accès non autorisé"), introuvable.getBody());
	}

	// Adresse tapée à la main, corps absent, sans connexion ou dirigeant : jamais jusqu'au service.
	@Test
	void demandesInvalides() {
		connecte(lucie);
		assertEquals(HttpStatus.NOT_FOUND, controller.valider(AUTORISATION, "abc", "5", demande).getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, controller.enregistrer(AUTORISATION, "42", "x", demande).getStatusCode());
		ResponseEntity<Object> sansCorps = controller.valider(AUTORISATION, "42", "5", null);
		assertEquals(HttpStatus.BAD_REQUEST, sansCorps.getStatusCode());
		assertEquals(new ErreurResponse("Indiquez les informations de la facture"), sansCorps.getBody());

		when(sessions.consulter(null)).thenReturn(ResultatSession.nonConnecte());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.valider(null, "42", "5", demande).getStatusCode());
		connecte(new Compte("sophie@test.fr", "x", Role.DIRIGEANT, true, "Sophie Dupont"));
		assertEquals(HttpStatus.FORBIDDEN, controller.enregistrer(AUTORISATION, "42", "5", demande).getStatusCode());

		verify(fiches, never()).valider(any(), any(), any(), any());
		verify(fiches, never()).enregistrer(any(), any(), any(), any());
	}

}
