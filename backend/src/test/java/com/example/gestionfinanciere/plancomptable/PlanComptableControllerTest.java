package com.example.gestionfinanciere.plancomptable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.dossier.DossierController.ErreursFormulaire;
import com.example.gestionfinanciere.plancomptable.ResultatCompte.Statut;

class PlanComptableControllerTest {

	private static final String AUTORISATION = "Bearer jeton";

	private final PlanComptableService plans = mock(PlanComptableService.class);
	private final SessionService sessions = mock(SessionService.class);
	private final PlanComptableController controller = new PlanComptableController(plans, sessions);

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, Instant.EPOCH, Instant.EPOCH);
	private final Compte lucie = Compte.administrateurDe(cabinet, "lucie@test.fr", "x", "Lucie Martin");
	private final VueCompte moulins = new VueCompte("401MOULINS", "Moulins Bertrand", true, true);

	@BeforeEach
	void session() {
		when(sessions.consulter("jeton")).thenReturn(ResultatSession.active(lucie));
		when(sessions.consulter(null)).thenReturn(ResultatSession.nonConnecte());
	}

	@Test
	void consulter() {
		PlanComptable plan = new PlanComptable(List.of(moulins), List.of());
		when(plans.consulter(lucie, 42L)).thenReturn(Optional.of(plan));

		ResponseEntity<Object> reponse = controller.consulter(AUTORISATION, "42");
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(plan, reponse.getBody());
		assertEquals(HttpStatus.NOT_FOUND, controller.consulter(AUTORISATION, "99").getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, controller.consulter(AUTORISATION, "abc").getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, controller.consulter(null, "42").getStatusCode());
	}

	@Test
	void creer() {
		DemandeCompte demande = new DemandeCompte("401MOULINS", "Moulins Bertrand");
		when(plans.creer(lucie, 42L, demande)).thenReturn(ResultatCompte.de(Statut.CREE, moulins));

		ResponseEntity<Object> reponse = controller.creer(AUTORISATION, "42", demande);
		assertEquals(HttpStatus.CREATED, reponse.getStatusCode());
		assertEquals(moulins, reponse.getBody());
	}

	@Test
	void changerActivationEtSupprimer() {
		DemandeActivation desactiver = new DemandeActivation(false);
		VueCompte inactif = new VueCompte("401MOULINS", "Moulins Bertrand", true, false);
		when(plans.changerActivation(lucie, 42L, "401MOULINS", desactiver))
				.thenReturn(ResultatCompte.de(Statut.MODIFIE, inactif));
		when(plans.supprimer(lucie, 42L, "401MOULINS")).thenReturn(ResultatCompte.de(Statut.SUPPRIME));

		assertEquals(inactif, controller.changerActivation(AUTORISATION, "42", "401MOULINS", desactiver).getBody());
		assertEquals(HttpStatus.NO_CONTENT, controller.supprimer(AUTORISATION, "42", "401MOULINS").getStatusCode());
	}

	// CA2 et refus : codes HTTP et messages affichés par l'écran.
	@Test
	void refus() {
		ResponseEntity<Object> invalide = PlanComptableController
				.reponse(ResultatCompte.invalide(Map.of("numero", ValidationCompte.NUMERO_INVALIDE)));
		assertEquals(HttpStatus.BAD_REQUEST, invalide.getStatusCode());
		assertEquals(new ErreursFormulaire(PlanComptableController.MESSAGE_FORMULAIRE,
				Map.of("numero", ValidationCompte.NUMERO_INVALIDE)), invalide.getBody());

		assertEquals(HttpStatus.CONFLICT,
				PlanComptableController.reponse(ResultatCompte.de(Statut.DEJA_EXISTANT)).getStatusCode());
		ResponseEntity<Object> general = PlanComptableController.reponse(ResultatCompte.de(Statut.COMPTE_GENERAL));
		assertEquals(HttpStatus.CONFLICT, general.getStatusCode());
		assertEquals(new ErreurResponse(PlanComptableController.MESSAGE_COMPTE_GENERAL), general.getBody());
		assertEquals(HttpStatus.NOT_FOUND,
				PlanComptableController.reponse(ResultatCompte.de(Statut.COMPTE_INTROUVABLE)).getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND,
				PlanComptableController.reponse(ResultatCompte.de(Statut.DOSSIER_INTROUVABLE)).getStatusCode());
	}

}
