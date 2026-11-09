package com.example.gestionfinanciere.facture;

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

class FactureControllerTest {

	private static final String AUTORISATION = "Bearer jeton";
	private static final ErreurResponse INTROUVABLE = new ErreurResponse("Dossier introuvable ou accès non autorisé");

	private final FactureService factures = mock(FactureService.class);
	private final SessionService sessions = mock(SessionService.class);
	private final FactureController controller = new FactureController(factures, sessions);

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, Instant.EPOCH, Instant.EPOCH);
	private final Compte jean = Compte.administrateurDe(cabinet, "jean@test.fr", "x", "Jean Dupont");
	private final ListeFactures liste = new ListeFactures(
			List.of(new LigneFacture(1L, "edf.pdf", Instant.EPOCH, "Jean Dupont", Facture.Statut.RECUE, true)), false);

	private void connecte(Compte compte) {
		when(sessions.consulter("jeton")).thenReturn(ResultatSession.active(compte));
	}

	// RG1 : onglet achats (factures fournisseurs) ou ventes (factures clients).
	@Test
	void rg1AchatsEtVentes() {
		connecte(jean);
		ListeFactures vide = new ListeFactures(List.of(), false);
		when(factures.lister(jean, 42L, Facture.Sens.ACHAT)).thenReturn(Optional.of(liste));
		when(factures.lister(jean, 42L, Facture.Sens.VENTE)).thenReturn(Optional.of(vide));

		ResponseEntity<Object> achats = controller.lister(AUTORISATION, "42", "ACHAT");
		assertEquals(HttpStatus.OK, achats.getStatusCode());
		assertEquals(liste, achats.getBody());
		assertEquals(vide, controller.lister(AUTORISATION, "42", " vente ").getBody());
	}

	@Test
	void ongletInconnu() {
		connecte(jean);
		ResponseEntity<Object> reponse = controller.lister(AUTORISATION, "42", "AUTRE");
		assertEquals(HttpStatus.BAD_REQUEST, reponse.getStatusCode());
		assertEquals(new ErreurResponse("Indiquez ACHAT ou VENTE"), reponse.getBody());
	}

	// US-11 RG3 : dossier d'un autre cabinet ou adresse tapée à la main.
	@Test
	void dossierIntrouvable() {
		connecte(jean);
		when(factures.lister(jean, 43L, Facture.Sens.ACHAT)).thenReturn(Optional.empty());
		for (String id : List.of("43", "abc")) {
			ResponseEntity<Object> reponse = controller.lister(AUTORISATION, id, "ACHAT");
			assertEquals(HttpStatus.NOT_FOUND, reponse.getStatusCode());
			assertEquals(INTROUVABLE, reponse.getBody());
		}
	}

	@Test
	void sessionEtRole() {
		when(sessions.consulter(null)).thenReturn(ResultatSession.nonConnecte());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.lister(null, "42", "ACHAT").getStatusCode());
		connecte(new Compte("sophie@test.fr", "x", Role.DIRIGEANT, true, "Sophie Martin"));
		assertEquals(HttpStatus.FORBIDDEN, controller.lister(AUTORISATION, "42", "ACHAT").getStatusCode());
		assertEquals(HttpStatus.FORBIDDEN, controller.ajouterExemples(AUTORISATION, "42").getStatusCode());
		verify(factures, never()).lister(any(), any(), any());
		verify(factures, never()).ajouterExemples(any(), any());
	}

	@Test
	void exemples() {
		connecte(jean);
		when(factures.ajouterExemples(jean, 42L)).thenReturn(true);
		assertEquals(HttpStatus.CREATED, controller.ajouterExemples(AUTORISATION, "42").getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, controller.ajouterExemples(AUTORISATION, "43").getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, controller.ajouterExemples(AUTORISATION, "x").getStatusCode());
	}

}
