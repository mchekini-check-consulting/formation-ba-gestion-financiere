package com.example.gestionfinanciere.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.gestionfinanciere.cabinet.InscriptionController.CabinetCree;
import com.example.gestionfinanciere.cabinet.InscriptionController.CodeRequest;
import com.example.gestionfinanciere.cabinet.InscriptionController.ErreurCode;
import com.example.gestionfinanciere.cabinet.InscriptionController.ErreursFormulaire;
import com.example.gestionfinanciere.cabinet.InscriptionController.RenvoiRequest;
import com.example.gestionfinanciere.cabinet.InscriptionController.RenvoiTropTot;
import com.example.gestionfinanciere.cabinet.ResultatCodeInscription.Statut;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.DefiCode;
import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatRenvoi;
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.notification.EnvoiImpossibleException;

class InscriptionControllerTest {

	private final InscriptionService inscription = mock(InscriptionService.class);
	private final SessionService sessions = mock(SessionService.class);
	private final InscriptionController controller = new InscriptionController(inscription, sessions);
	private final DemandeInscription demande = ValidationInscriptionTest.claire();

	private ResponseEntity<Object> code(ResultatCodeInscription resultat) {
		when(inscription.validerCode("defi", "482917")).thenReturn(resultat);
		return controller.validerCode(new CodeRequest("defi", "482917"));
	}

	@Test
	void etape1CodeEnvoye() {
		DefiCode defi = new DefiCode("defi", "c•••••@cabinet-test.fr");
		when(inscription.demarrer(demande)).thenReturn(ResultatInscription.codeEnvoye(defi));
		ResponseEntity<Object> reponse = controller.demarrer(demande);
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(defi, reponse.getBody());
	}

	@Test
	void etape1FormulaireInvalide() {
		when(inscription.demarrer(demande))
				.thenReturn(ResultatInscription.invalide(Map.of("siren", ValidationInscription.SIREN_INVALIDE)));
		ResponseEntity<Object> reponse = controller.demarrer(demande);
		assertEquals(HttpStatus.BAD_REQUEST, reponse.getStatusCode());
		assertEquals(new ErreursFormulaire("Certains champs sont à corriger",
				Map.of("siren", ValidationInscription.SIREN_INVALIDE)), reponse.getBody());
	}

	@Test
	void ca2SirenDejaUtilise() {
		when(inscription.demarrer(demande)).thenReturn(ResultatInscription.sirenDejaUtilise());
		ResponseEntity<Object> reponse = controller.demarrer(demande);
		assertEquals(HttpStatus.CONFLICT, reponse.getStatusCode());
		String message = "Un cabinet existe déjà avec ce SIREN. Demandez à son administrateur de vous inviter.";
		assertEquals(new ErreursFormulaire(message, Map.of("siren", message)), reponse.getBody());
	}

	@Test
	void emailDejaUtilise() {
		when(inscription.demarrer(demande)).thenReturn(ResultatInscription.emailDejaUtilise());
		ResponseEntity<Object> reponse = controller.demarrer(demande);
		assertEquals(HttpStatus.CONFLICT, reponse.getStatusCode());
		assertEquals(Map.of("email", InscriptionController.MESSAGE_EMAIL_DEJA_UTILISE),
				((ErreursFormulaire) reponse.getBody()).erreurs());
	}

	@Test
	void etape1EmailImpossible() {
		when(inscription.demarrer(demande)).thenThrow(new EnvoiImpossibleException("api", null));
		ResponseEntity<Object> reponse = controller.demarrer(demande);
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, reponse.getStatusCode());
		assertEquals(new ErreurResponse("L’email n’a pas pu être envoyé. Réessayez."), reponse.getBody());
	}

	@Test
	void ca1BonCodeConnecteLAdministratrice() {
		Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, Instant.EPOCH, Instant.EPOCH);
		Compte claire = Compte.administrateurDe(cabinet, "claire.martin@cabinet-test.fr", "x", "Claire Martin");
		when(sessions.ouvrir(claire)).thenReturn("jeton-session");
		ResponseEntity<Object> reponse = code(ResultatCodeInscription.cabinetCree(claire));
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(new CabinetCree("jeton-session", Role.COLLABORATEUR, "Claire Martin", "Cabinet Test"),
				reponse.getBody());
	}

	@Test
	void codeFaux() {
		ResponseEntity<Object> reponse = code(ResultatCodeInscription.faux(2));
		assertEquals(HttpStatus.UNAUTHORIZED, reponse.getStatusCode());
		assertEquals(new ErreurCode("Code incorrect. Il vous reste 2 tentatives.", 2, false), reponse.getBody());
		assertEquals("Code incorrect. Il vous reste 1 tentative.", InscriptionController.messageCodeFaux(1));
	}

	@Test
	void codeExpire() {
		ResponseEntity<Object> reponse = code(ResultatCodeInscription.autre(Statut.EXPIRE, 3));
		assertEquals(HttpStatus.GONE, reponse.getStatusCode());
		assertEquals(new ErreurCode(InscriptionController.MESSAGE_CODE_EXPIRE, 3, false), reponse.getBody());
	}

	@Test
	void casQuiRamenentAuFormulaire() {
		assertEquals(new ErreurCode(InscriptionController.MESSAGE_TROP_DE_CODES, 0, true),
				code(ResultatCodeInscription.autre(Statut.TROP_DE_CODES_FAUX, 0)).getBody());
		assertEquals(new ErreurCode(InscriptionController.MESSAGE_DEMANDE_INVALIDE, 0, true),
				code(ResultatCodeInscription.autre(Statut.DEMANDE_INVALIDE, 0)).getBody());
		ResponseEntity<Object> siren = code(ResultatCodeInscription.autre(Statut.SIREN_DEJA_UTILISE, 0));
		assertEquals(HttpStatus.CONFLICT, siren.getStatusCode());
		assertEquals(new ErreurCode(InscriptionController.MESSAGE_SIREN_DEJA_UTILISE, 0, true), siren.getBody());
		assertEquals(new ErreurCode(InscriptionController.MESSAGE_EMAIL_DEJA_UTILISE, 0, true),
				code(ResultatCodeInscription.autre(Statut.EMAIL_DEJA_UTILISE, 0)).getBody());
	}

	@Test
	void renvoiDuCode() {
		when(inscription.renvoyer("defi")).thenReturn(ResultatRenvoi.renvoye());
		ResponseEntity<Object> reponse = controller.renvoyerCode(new RenvoiRequest("defi"));
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertNull(reponse.getBody());
	}

	@Test
	void renvoiTropTotInvalideOuImpossible() {
		when(inscription.renvoyer("tot")).thenReturn(ResultatRenvoi.tropTot(42));
		ResponseEntity<Object> tot = controller.renvoyerCode(new RenvoiRequest("tot"));
		assertEquals(HttpStatus.TOO_MANY_REQUESTS, tot.getStatusCode());
		assertEquals(new RenvoiTropTot("Vous pourrez demander un nouveau code dans 42 s.", 42), tot.getBody());

		when(inscription.renvoyer("inconnu")).thenReturn(ResultatRenvoi.demandeInvalide());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.renvoyerCode(new RenvoiRequest("inconnu")).getStatusCode());

		when(inscription.renvoyer("ko")).thenThrow(new EnvoiImpossibleException("api", null));
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, controller.renvoyerCode(new RenvoiRequest("ko")).getStatusCode());
	}

}
