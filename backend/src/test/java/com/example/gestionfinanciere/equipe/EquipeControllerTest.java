package com.example.gestionfinanciere.equipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.compte.RoleCabinet;
import com.example.gestionfinanciere.compte.SessionController.ErreurSession;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.equipe.EquipeController.ErreursFormulaire;
import com.example.gestionfinanciere.equipe.EquipeController.InvitationEnvoyee;
import com.example.gestionfinanciere.notification.EnvoiImpossibleException;

class EquipeControllerTest {

	private static final String AUTORISATION = "Bearer jeton";

	private final EquipeService equipe = mock(EquipeService.class);
	private final SessionService sessions = mock(SessionService.class);
	private final EquipeController controller = new EquipeController(equipe, sessions);

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, Instant.EPOCH, Instant.EPOCH);
	private final Compte claire = Compte.administrateurDe(cabinet, "claire@test.fr", "x", "Claire Martin");
	private final DemandeInvitation demande = EquipeServiceTest.lucie();
	private final Membre lucie = new Membre(2L, "Lucie Moreau", "lucie.moreau@cabinet-test.fr",
			RoleCabinet.COMPTABLE, Membre.Statut.INVITE);

	private void connecte(Compte compte) {
		when(sessions.consulter("jeton")).thenReturn(ResultatSession.active(compte));
	}

	private ResponseEntity<Object> inviter() {
		return controller.inviter(AUTORISATION, demande);
	}

	@Test
	void listeDeLEquipe() {
		connecte(claire);
		when(equipe.membres(claire)).thenReturn(List.of(lucie));
		ResponseEntity<Object> reponse = controller.membres(AUTORISATION);
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(List.of(lucie), reponse.getBody());
	}

	@Test
	void ca1InvitationEnvoyee() {
		connecte(claire);
		when(equipe.inviter(claire, demande)).thenReturn(ResultatInvitation.envoyee(lucie));
		ResponseEntity<Object> reponse = inviter();
		assertEquals(HttpStatus.CREATED, reponse.getStatusCode());
		assertEquals(new InvitationEnvoyee(lucie), reponse.getBody());
	}

	@Test
	void ca2EmailDejaUtilise() {
		connecte(claire);
		when(equipe.inviter(claire, demande)).thenReturn(ResultatInvitation.emailDejaUtilise());
		ResponseEntity<Object> reponse = inviter();
		assertEquals(HttpStatus.CONFLICT, reponse.getStatusCode());
		assertEquals(new ErreursFormulaire("Cette adresse est déjà utilisée",
				Map.of("email", "Cette adresse est déjà utilisée")), reponse.getBody());
	}

	@Test
	void formulaireInvalide() {
		connecte(claire);
		when(equipe.inviter(claire, demande))
				.thenReturn(ResultatInvitation.invalide(Map.of("role", ValidationInvitation.ROLE_INVALIDE)));
		ResponseEntity<Object> reponse = inviter();
		assertEquals(HttpStatus.BAD_REQUEST, reponse.getStatusCode());
		assertEquals(new ErreursFormulaire("Certains champs sont à corriger",
				Map.of("role", ValidationInvitation.ROLE_INVALIDE)), reponse.getBody());
	}

	@Test
	void emailImpossible() {
		connecte(claire);
		when(equipe.inviter(claire, demande)).thenThrow(new EnvoiImpossibleException("api", null));
		ResponseEntity<Object> reponse = inviter();
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, reponse.getStatusCode());
		assertEquals(new ErreurResponse(EquipeController.MESSAGE_EMAIL_IMPOSSIBLE), reponse.getBody());
	}

	@Test
	void rg1ReserveAuxAdministrateurs() {
		Compte lucieActive = Compte.invite(cabinet, "lucie@test.fr", "x", "Lucie Moreau", RoleCabinet.COMPTABLE);
		connecte(lucieActive);
		assertEquals(HttpStatus.FORBIDDEN, controller.membres(AUTORISATION).getStatusCode());
		assertEquals(new ErreurResponse(EquipeController.MESSAGE_RESERVE), inviter().getBody());

		// Compte sans cabinet (comptes de démonstration, dirigeants clients).
		connecte(new Compte("jean@test.fr", "x", Role.COLLABORATEUR, true, "Jean Dupont"));
		assertEquals(HttpStatus.FORBIDDEN, inviter().getStatusCode());
		verify(equipe, never()).inviter(any(), any());
	}

	@Test
	void sansSessionOuSessionExpiree() {
		when(sessions.consulter(null)).thenReturn(ResultatSession.nonConnecte());
		ResponseEntity<Object> sans = controller.membres(null);
		assertEquals(HttpStatus.UNAUTHORIZED, sans.getStatusCode());
		assertEquals(new ErreurSession("Vous n'êtes pas connecté", "NON_CONNECTE"), sans.getBody());

		when(sessions.consulter("jeton")).thenReturn(ResultatSession.expiree());
		ResponseEntity<Object> expiree = inviter();
		assertEquals(HttpStatus.UNAUTHORIZED, expiree.getStatusCode());
		assertEquals(new ErreurSession("Votre session a expiré", "SESSION_EXPIREE"), expiree.getBody());
	}

}
