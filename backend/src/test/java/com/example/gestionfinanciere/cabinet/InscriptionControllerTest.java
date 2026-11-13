package com.example.gestionfinanciere.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.gestionfinanciere.cabinet.InscriptionController.CabinetCree;
import com.example.gestionfinanciere.cabinet.InscriptionController.ErreursFormulaire;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.compte.SessionService;

class InscriptionControllerTest {

	private final InscriptionService inscription = mock(InscriptionService.class);
	private final SessionService sessions = mock(SessionService.class);
	private final InscriptionController controller = new InscriptionController(inscription, sessions);
	private final DemandeInscription demande = ValidationInscriptionTest.claire();

	@Test
	void ca1CabinetCreeEtAdministratriceConnectee() {
		Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, Instant.EPOCH, Instant.EPOCH);
		Compte claire = Compte.administrateurDe(cabinet, "claire.martin@cabinet-test.fr", "x", "Claire Martin");
		when(inscription.creer(demande)).thenReturn(ResultatInscription.cabinetCree(claire));
		when(sessions.ouvrir(claire)).thenReturn("jeton-session");
		ResponseEntity<Object> reponse = controller.creer(demande);
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(new CabinetCree("jeton-session", Role.COLLABORATEUR, "Claire Martin", "Cabinet Test"),
				reponse.getBody());
	}

	@Test
	void formulaireInvalide() {
		when(inscription.creer(demande))
				.thenReturn(ResultatInscription.invalide(Map.of("siren", ValidationInscription.SIREN_INVALIDE)));
		ResponseEntity<Object> reponse = controller.creer(demande);
		assertEquals(HttpStatus.BAD_REQUEST, reponse.getStatusCode());
		assertEquals(new ErreursFormulaire("Certains champs sont à corriger",
				Map.of("siren", ValidationInscription.SIREN_INVALIDE)), reponse.getBody());
	}

	@Test
	void ca2SirenDejaUtilise() {
		when(inscription.creer(demande)).thenReturn(ResultatInscription.sirenDejaUtilise());
		ResponseEntity<Object> reponse = controller.creer(demande);
		assertEquals(HttpStatus.CONFLICT, reponse.getStatusCode());
		String message = "Un cabinet existe déjà avec ce SIREN. Demandez à son administrateur de vous inviter.";
		assertEquals(new ErreursFormulaire(message, Map.of("siren", message)), reponse.getBody());
	}

	@Test
	void emailDejaUtilise() {
		when(inscription.creer(demande)).thenReturn(ResultatInscription.emailDejaUtilise());
		ResponseEntity<Object> reponse = controller.creer(demande);
		assertEquals(HttpStatus.CONFLICT, reponse.getStatusCode());
		assertEquals(Map.of("email", InscriptionController.MESSAGE_EMAIL_DEJA_UTILISE),
				((ErreursFormulaire) reponse.getBody()).erreurs());
	}

}
