package com.example.gestionfinanciere.facture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
			List.of(new LigneFacture(1L, "edf.pdf", Instant.EPOCH, "Jean Dupont", Facture.Statut.RECUE, true, null,
					Facture.Statut.RECUE.suivants(), null, false)),
			false);

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

	// US-13 RG1 : aperçu dans la page ; RG2 : téléchargement du fichier d'origine.
	@Test
	void us13ApercuEtTelechargement() {
		connecte(jean);
		FichierFacture fichier = new FichierFacture("facture électricité.pdf", "application/pdf", new byte[] { 1, 2 });
		when(factures.ouvrir(jean, 42L, 5L, false)).thenReturn(Optional.of(fichier));
		when(factures.ouvrir(jean, 42L, 5L, true)).thenReturn(Optional.of(fichier));

		ResponseEntity<Object> apercu = controller.fichier(AUTORISATION, "42", "5", false);
		assertEquals(HttpStatus.OK, apercu.getStatusCode());
		assertEquals(MediaType.APPLICATION_PDF, apercu.getHeaders().getContentType());
		assertArrayEquals(new byte[] { 1, 2 }, (byte[]) apercu.getBody());
		assertTrue(apercu.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("inline;"));
		assertEquals("no-store", apercu.getHeaders().getCacheControl());
		assertEquals("nosniff", apercu.getHeaders().getFirst("X-Content-Type-Options"));

		ResponseEntity<Object> telechargement = controller.fichier(AUTORISATION, "42", "5", true);
		assertTrue(telechargement.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
				.startsWith("attachment; filename="));
	}

	@Test
	void us13NomEtTypeDuFichier() {
		connecte(jean);
		VueFacture vue = new VueFacture(5L, "edf.pdf", "application/pdf", Facture.Sens.ACHAT, null);
		when(factures.consulter(jean, 42L, 5L)).thenReturn(Optional.of(vue));
		assertEquals(vue, controller.consulter(AUTORISATION, "42", "5").getBody());
	}

	// US-13 RG3 : sans connexion, aucun fichier ; sans droits, « Facture introuvable ou accès non autorisé ».
	@Test
	void us13ControleDAcces() {
		when(sessions.consulter(null)).thenReturn(ResultatSession.nonConnecte());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.fichier(null, "42", "5", false).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.consulter(null, "42", "5").getStatusCode());

		connecte(jean);
		when(factures.ouvrir(any(), any(), any(), anyBoolean())).thenReturn(Optional.empty());
		when(factures.consulter(any(), any(), any())).thenReturn(Optional.empty());
		ErreurResponse introuvable = new ErreurResponse("Facture introuvable ou accès non autorisé");
		for (String[] ids : new String[][] { { "42", "6" }, { "x", "5" }, { "42", "y" } }) {
			ResponseEntity<Object> fichier = controller.fichier(AUTORISATION, ids[0], ids[1], true);
			assertEquals(HttpStatus.NOT_FOUND, fichier.getStatusCode());
			assertEquals(introuvable, fichier.getBody());
			assertEquals(introuvable, controller.consulter(AUTORISATION, ids[0], ids[1]).getBody());
		}

		connecte(new Compte("sophie@test.fr", "x", Role.DIRIGEANT, true, "Sophie Martin"));
		assertEquals(HttpStatus.FORBIDDEN, controller.fichier(AUTORISATION, "42", "5", false).getStatusCode());
		assertEquals(HttpStatus.FORBIDDEN, controller.consulter(AUTORISATION, "42", "5").getStatusCode());
	}

	private static FactureController.ChangementStatutRequest demande(String statut, String motif) {
		return new FactureController.ChangementStatutRequest(statut, motif);
	}

	// US-51 CA1 : passage autorisé -> la ligne à jour.
	@Test
	void us51ChangerStatut() {
		connecte(jean);
		LigneFacture enCours = new LigneFacture(5L, "edf.pdf", Instant.EPOCH, "Jean Dupont", Facture.Statut.EN_COURS,
				false, null, Facture.Statut.EN_COURS.suivants(), null, false);
		when(factures.changerStatut(jean, 42L, 5L, Facture.Statut.EN_COURS, null))
				.thenReturn(new ResultatStatut(ResultatStatut.Issue.CHANGE, enCours));

		ResponseEntity<Object> reponse = controller.changerStatut(AUTORISATION, "42", "5", demande(" en_cours ", null));
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(enCours, reponse.getBody());
	}

	// US-51 CA3 (motif manquant), CA4 (passage interdit), statut inconnu, facture introuvable.
	@Test
	void us51Refus() {
		connecte(jean);
		String tropLong = "x".repeat(300);
		when(factures.changerStatut(jean, 42L, 5L, Facture.Statut.A_COMPLETER, ""))
				.thenReturn(ResultatStatut.refuse(ResultatStatut.Issue.MOTIF_MANQUANT));
		when(factures.changerStatut(jean, 42L, 5L, Facture.Statut.A_COMPLETER, tropLong))
				.thenReturn(ResultatStatut.refuse(ResultatStatut.Issue.MOTIF_TROP_LONG));
		when(factures.changerStatut(jean, 42L, 5L, Facture.Statut.TRAITEE, null))
				.thenReturn(ResultatStatut.refuse(ResultatStatut.Issue.PASSAGE_INTERDIT));
		when(factures.changerStatut(jean, 42L, 6L, Facture.Statut.EN_COURS, null))
				.thenReturn(ResultatStatut.refuse(ResultatStatut.Issue.INTROUVABLE));

		ResponseEntity<Object> sansMotif = controller.changerStatut(AUTORISATION, "42", "5", demande("A_COMPLETER", ""));
		assertEquals(HttpStatus.BAD_REQUEST, sansMotif.getStatusCode());
		assertEquals(new ErreurResponse("Indiquez le motif"), sansMotif.getBody());

		assertEquals(new ErreurResponse(FactureController.MESSAGE_MOTIF_TROP_LONG),
				controller.changerStatut(AUTORISATION, "42", "5", demande("A_COMPLETER", tropLong)).getBody());

		ResponseEntity<Object> interdit = controller.changerStatut(AUTORISATION, "42", "5", demande("TRAITEE", null));
		assertEquals(HttpStatus.CONFLICT, interdit.getStatusCode());
		assertEquals(new ErreurResponse(FactureController.MESSAGE_PASSAGE_INTERDIT), interdit.getBody());

		assertEquals(HttpStatus.NOT_FOUND,
				controller.changerStatut(AUTORISATION, "42", "6", demande("EN_COURS", null)).getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND,
				controller.changerStatut(AUTORISATION, "x", "5", demande("EN_COURS", null)).getStatusCode());

		for (FactureController.ChangementStatutRequest inconnue : new FactureController.ChangementStatutRequest[] {
				null, demande(null, null), demande("PERDUE", null) }) {
			ResponseEntity<Object> reponse = controller.changerStatut(AUTORISATION, "42", "5", inconnue);
			assertEquals(HttpStatus.BAD_REQUEST, reponse.getStatusCode());
			assertEquals(new ErreurResponse("Statut inconnu"), reponse.getBody());
		}
	}

	// US-51 : réservé aux collaborateurs du cabinet ; session obligatoire.
	@Test
	void us51SessionEtRole() {
		when(sessions.consulter(null)).thenReturn(ResultatSession.nonConnecte());
		assertEquals(HttpStatus.UNAUTHORIZED,
				controller.changerStatut(null, "42", "5", demande("EN_COURS", null)).getStatusCode());
		connecte(new Compte("sophie@test.fr", "x", Role.DIRIGEANT, true, "Sophie Martin"));
		assertEquals(HttpStatus.FORBIDDEN,
				controller.changerStatut(AUTORISATION, "42", "5", demande("EN_COURS", null)).getStatusCode());
		verify(factures, never()).changerStatut(any(), any(), any(), any(), any());
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
