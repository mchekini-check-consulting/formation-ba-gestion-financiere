package com.example.gestionfinanciere.facture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.SessionService;

class EspaceClientControllerTest {

	private static final String AUTORISATION = "Bearer jeton";
	private static final byte[] PDF = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);

	private final DepotFactureService depots = mock(DepotFactureService.class);
	private final SessionService sessions = mock(SessionService.class);
	private final EspaceClientController controller = new EspaceClientController(depots, sessions);

	private final Compte sophie = Compte.dirigeantInvite(42L, "sophie@test.fr", "x", "Sophie Martin");
	private final FactureDeposee ticket = new FactureDeposee(5L, "ticket.jpg", Instant.EPOCH, Facture.Sens.ACHAT,
			true);
	private final MockMultipartFile fichier = new MockMultipartFile("fichier", "facture.pdf", "application/pdf", PDF);

	private void connecte(Compte compte) {
		when(sessions.consulter("jeton")).thenReturn(ResultatSession.active(compte));
	}

	@BeforeEach
	void base() {
		sophie.activer("y");
	}

	// US-16 RG1 : dépôts de l'onglet.
	@Test
	void listerSesDepots() {
		connecte(sophie);
		when(depots.lister(sophie, Facture.Sens.ACHAT)).thenReturn(List.of(ticket));

		ResponseEntity<Object> reponse = controller.lister(AUTORISATION, " achat ");
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(List.of(ticket), reponse.getBody());
		assertEquals(HttpStatus.BAD_REQUEST, controller.lister(AUTORISATION, "AUTRE").getStatusCode());
	}

	// US-15 CA1 / US-38 : facture déposée dans l'onglet choisi.
	@Test
	void deposer() {
		connecte(sophie);
		when(depots.deposer(sophie, Facture.Sens.VENTE, "facture.pdf", PDF)).thenReturn(ResultatDepot.deposee(ticket));

		ResponseEntity<Object> reponse = controller.deposer(AUTORISATION, "VENTE", fichier);
		assertEquals(HttpStatus.CREATED, reponse.getStatusCode());
		assertEquals(ticket, reponse.getBody());
	}

	// US-15 CA2 / RG1 : messages de refus.
	@Test
	void deposerRefuse() {
		connecte(sophie);
		when(depots.deposer(sophie, Facture.Sens.ACHAT, "facture.pdf", PDF))
				.thenReturn(ResultatDepot.refuse(ResultatDepot.Statut.FORMAT_REFUSE));
		ResponseEntity<Object> format = controller.deposer(AUTORISATION, "ACHAT", fichier);
		assertEquals(HttpStatus.BAD_REQUEST, format.getStatusCode());
		assertEquals(new ErreurResponse("Format non accepté : PDF, JPG ou PNG uniquement"), format.getBody());

		when(depots.deposer(sophie, Facture.Sens.ACHAT, "facture.pdf", PDF))
				.thenReturn(ResultatDepot.refuse(ResultatDepot.Statut.TROP_VOLUMINEUX));
		ResponseEntity<Object> taille = controller.deposer(AUTORISATION, "ACHAT", fichier);
		assertEquals(new ErreurResponse("Fichier trop volumineux : 10 Mo maximum"), taille.getBody());

		assertEquals(HttpStatus.BAD_REQUEST, controller.deposer(AUTORISATION, "ACHAT", null).getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST, controller.deposer(AUTORISATION, "AUTRE", fichier).getStatusCode());
	}

	@Test
	void fichierTropGrosRefuseAvantLecture() throws IOException {
		connecte(sophie);
		MultipartFile gros = mock(MultipartFile.class);
		when(gros.getSize()).thenReturn(DepotFactureService.TAILLE_MAX + 1);

		ResponseEntity<Object> reponse = controller.deposer(AUTORISATION, "ACHAT", gros);
		assertEquals(new ErreurResponse(EspaceClientController.MESSAGE_TAILLE), reponse.getBody());
		verify(gros, never()).getBytes();
		assertEquals(HttpStatus.BAD_REQUEST, new FichierTropVolumineuxHandler().tropVolumineux().getStatusCode());
	}

	@Test
	void fichierIlisible() throws IOException {
		connecte(sophie);
		MultipartFile casse = mock(MultipartFile.class);
		when(casse.getBytes()).thenThrow(new IOException("coupé"));
		assertThrows(UncheckedIOException.class, () -> controller.deposer(AUTORISATION, "ACHAT", casse));
	}

	// US-16 CA1 et CA2.
	@Test
	void supprimer() {
		connecte(sophie);
		when(depots.supprimer(sophie, 5L)).thenReturn(DepotFactureService.ResultatSuppression.SUPPRIMEE);
		when(depots.supprimer(sophie, 6L)).thenReturn(DepotFactureService.ResultatSuppression.DEJA_OUVERTE);
		when(depots.supprimer(sophie, 7L)).thenReturn(DepotFactureService.ResultatSuppression.INTROUVABLE);

		assertEquals(HttpStatus.NO_CONTENT, controller.supprimer(AUTORISATION, "5").getStatusCode());
		ResponseEntity<Object> ouverte = controller.supprimer(AUTORISATION, "6");
		assertEquals(HttpStatus.CONFLICT, ouverte.getStatusCode());
		assertEquals(new ErreurResponse(EspaceClientController.MESSAGE_DEJA_OUVERTE), ouverte.getBody());
		assertEquals(HttpStatus.NOT_FOUND, controller.supprimer(AUTORISATION, "7").getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, controller.supprimer(AUTORISATION, "abc").getStatusCode());
	}

	// Réservé au dirigeant actif d'un dossier (US-07 : session obligatoire).
	@Test
	void sessionEtRole() {
		when(sessions.consulter(null)).thenReturn(ResultatSession.nonConnecte());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.lister(null, "ACHAT").getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.deposer(null, "ACHAT", fichier).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.supprimer(null, "5").getStatusCode());

		Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, Instant.EPOCH, Instant.EPOCH);
		connecte(Compte.administrateurDe(cabinet, "jean@test.fr", "x", "Jean Dupont"));
		ResponseEntity<Object> interdit = controller.lister(AUTORISATION, "ACHAT");
		assertEquals(HttpStatus.FORBIDDEN, interdit.getStatusCode());
		assertEquals(new ErreurResponse(EspaceClientController.MESSAGE_RESERVE), interdit.getBody());

		connecte(Compte.dirigeantInvite(42L, "invite@test.fr", "x", "Pas encore actif"));
		assertEquals(HttpStatus.FORBIDDEN, controller.deposer(AUTORISATION, "ACHAT", fichier).getStatusCode());
		assertEquals(HttpStatus.FORBIDDEN, controller.supprimer(AUTORISATION, "5").getStatusCode());
		verify(depots, never()).lister(any(), any());
		verify(depots, never()).deposer(any(), any(), any(), any());
		verify(depots, never()).supprimer(any(), any());
	}

}
