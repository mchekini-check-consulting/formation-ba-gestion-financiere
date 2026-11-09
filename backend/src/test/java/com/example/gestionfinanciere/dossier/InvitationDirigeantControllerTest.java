package com.example.gestionfinanciere.dossier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.dossier.DossierController.ErreursFormulaire;
import com.example.gestionfinanciere.dossier.InvitationDirigeantController.InvitationEnvoyee;
import com.example.gestionfinanciere.notification.EnvoiImpossibleException;

class InvitationDirigeantControllerTest {

	private static final String AUTORISATION = "Bearer jeton";

	private final InvitationDirigeantService invitations = mock(InvitationDirigeantService.class);
	private final SessionService sessions = mock(SessionService.class);
	private final InvitationDirigeantController controller = new InvitationDirigeantController(invitations, sessions);

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, Instant.EPOCH, Instant.EPOCH);
	private final Compte jean = Compte.administrateurDe(cabinet, "jean@test.fr", "x", "Jean Dupont");
	private final DemandeInvitationDirigeant demande = InvitationDirigeantServiceTest.sophie();

	private void connecte(Compte compte) {
		when(sessions.consulter("jeton")).thenReturn(ResultatSession.active(compte));
	}

	@Test
	void rg1InvitationEnvoyee() {
		connecte(jean);
		when(invitations.inviter(jean, 42L, demande)).thenReturn(ResultatInvitationDirigeant.envoyee("lien"));
		ResponseEntity<Object> reponse = controller.inviter(AUTORISATION, "42", demande);
		assertEquals(HttpStatus.CREATED, reponse.getStatusCode());
		assertEquals(new InvitationEnvoyee(EtatEspaceClient.INVITE, "lien"), reponse.getBody());
	}

	@Test
	void caErreurEtRefus() {
		connecte(jean);
		Map<String, String> erreurs = Map.of("email", ValidationInvitationDirigeant.EMAIL_INVALIDE);
		when(invitations.inviter(jean, 42L, demande)).thenReturn(ResultatInvitationDirigeant.invalide(erreurs));
		ResponseEntity<Object> invalide = controller.inviter(AUTORISATION, "42", demande);
		assertEquals(HttpStatus.BAD_REQUEST, invalide.getStatusCode());
		assertEquals(new ErreursFormulaire("Certains champs sont à corriger", erreurs), invalide.getBody());

		when(invitations.inviter(jean, 42L, demande))
				.thenReturn(ResultatInvitationDirigeant.refus(ResultatInvitationDirigeant.Statut.EMAIL_DEJA_UTILISE));
		ResponseEntity<Object> doublon = controller.inviter(AUTORISATION, "42", demande);
		assertEquals(HttpStatus.CONFLICT, doublon.getStatusCode());
		assertEquals(new ErreursFormulaire("Cette adresse est déjà utilisée",
				Map.of("email", "Cette adresse est déjà utilisée")), doublon.getBody());

		when(invitations.inviter(jean, 42L, demande))
				.thenReturn(ResultatInvitationDirigeant.refus(ResultatInvitationDirigeant.Statut.ETAT_INCOMPATIBLE));
		assertEquals(new ErreurResponse("Le dirigeant de ce dossier est déjà invité"),
				controller.inviter(AUTORISATION, "42", demande).getBody());

		when(invitations.inviter(jean, 42L, demande))
				.thenReturn(ResultatInvitationDirigeant.refus(ResultatInvitationDirigeant.Statut.INTROUVABLE));
		ResponseEntity<Object> introuvable = controller.inviter(AUTORISATION, "42", demande);
		assertEquals(HttpStatus.NOT_FOUND, introuvable.getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, controller.inviter(AUTORISATION, "abc", demande).getStatusCode());
	}

	@Test
	void rg2Renvoi() {
		connecte(jean);
		when(invitations.renvoyer(jean, 42L)).thenReturn(ResultatInvitationDirigeant.envoyee(null));
		ResponseEntity<Object> renvoi = controller.renvoyer(AUTORISATION, "42");
		assertEquals(HttpStatus.OK, renvoi.getStatusCode());
		assertEquals(new InvitationEnvoyee(EtatEspaceClient.INVITE, null), renvoi.getBody());

		when(invitations.renvoyer(jean, 43L))
				.thenReturn(ResultatInvitationDirigeant.refus(ResultatInvitationDirigeant.Statut.ETAT_INCOMPATIBLE));
		ResponseEntity<Object> refus = controller.renvoyer(AUTORISATION, "43");
		assertEquals(HttpStatus.CONFLICT, refus.getStatusCode());
		assertEquals(new ErreurResponse("Aucune invitation en attente pour ce dossier"), refus.getBody());
		assertEquals(HttpStatus.NOT_FOUND, controller.renvoyer(AUTORISATION, "x").getStatusCode());
	}

	@Test
	void emailImpossible() {
		connecte(jean);
		when(invitations.inviter(jean, 42L, demande)).thenThrow(new EnvoiImpossibleException("panne", null));
		when(invitations.renvoyer(jean, 42L)).thenThrow(new EnvoiImpossibleException("panne", null));
		ErreurResponse message = new ErreurResponse("L'email d'invitation n'a pas pu être envoyé. Réessayez.");
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, controller.inviter(AUTORISATION, "42", demande).getStatusCode());
		assertEquals(message, controller.renvoyer(AUTORISATION, "42").getBody());
	}

	@Test
	void sessionEtRole() {
		when(sessions.consulter(null)).thenReturn(ResultatSession.nonConnecte());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.inviter(null, "42", demande).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.renvoyer(null, "42").getStatusCode());
		connecte(new Compte("sophie@test.fr", "x", Role.DIRIGEANT, true, "Sophie Martin"));
		assertEquals(HttpStatus.FORBIDDEN, controller.inviter(AUTORISATION, "42", demande).getStatusCode());
		assertEquals(HttpStatus.FORBIDDEN, controller.renvoyer(AUTORISATION, "42").getStatusCode());
		verify(invitations, never()).inviter(any(), any(), any());
		verify(invitations, never()).renvoyer(any(), any());
	}

}
