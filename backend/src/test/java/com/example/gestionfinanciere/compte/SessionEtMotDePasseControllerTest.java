package com.example.gestionfinanciere.compte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.gestionfinanciere.compte.MotDePasseController.DemandeEnvoyee;
import com.example.gestionfinanciere.compte.MotDePasseController.DemandeRequest;
import com.example.gestionfinanciere.compte.MotDePasseController.ErreurMotDePasse;
import com.example.gestionfinanciere.compte.MotDePasseController.LienRequest;
import com.example.gestionfinanciere.compte.MotDePasseController.NouveauMotDePasseRequest;
import com.example.gestionfinanciere.compte.SessionController.ErreurSession;
import com.example.gestionfinanciere.compte.SessionController.SessionActive;

class SessionEtMotDePasseControllerTest {

	private final SessionService sessions = mock(SessionService.class);
	private final SessionController session = new SessionController(sessions);
	private final ReinitialisationService reinitialisation = mock(ReinitialisationService.class);
	private final MotDePasseController motDePasse = new MotDePasseController(reinitialisation);
	private final Compte jean = new Compte("jean.dupont@cabinet-test.fr", "x", Role.COLLABORATEUR, true,
			"Jean Dupont", "0612345642");

	@Test
	void us07SessionActive() {
		when(sessions.consulter("jeton")).thenReturn(ResultatSession.active(jean));
		ResponseEntity<Object> reponse = session.consulter("Bearer jeton");
		assertEquals(HttpStatus.OK, reponse.getStatusCode());
		assertEquals(new SessionActive("Jean Dupont", Role.COLLABORATEUR, false), reponse.getBody());
	}

	@Test
	void us07Rg3SessionExpiree() {
		when(sessions.consulter("jeton")).thenReturn(ResultatSession.expiree());
		ResponseEntity<Object> reponse = session.consulter("Bearer jeton");
		assertEquals(HttpStatus.UNAUTHORIZED, reponse.getStatusCode());
		assertEquals(new ErreurSession("Votre session a expiré", "SESSION_EXPIREE"), reponse.getBody());
	}

	@Test
	void us07NonConnecte() {
		when(sessions.consulter(null)).thenReturn(ResultatSession.nonConnecte());
		ResponseEntity<Object> reponse = session.consulter(null);
		assertEquals(HttpStatus.UNAUTHORIZED, reponse.getStatusCode());
		assertEquals(new ErreurSession("Vous n'êtes pas connecté", "NON_CONNECTE"), reponse.getBody());
	}

	@Test
	void us07Rg1DeconnexionFermeLaSession() {
		assertEquals(HttpStatus.NO_CONTENT, session.seDeconnecter("Bearer jeton").getStatusCode());
		verify(sessions).fermer("jeton");
	}

	@Test
	void lectureDeLEnTeteAuthorization() {
		assertEquals("abc", SessionController.jeton("Bearer abc "));
		assertNull(SessionController.jeton("Basic abc"));
		assertNull(SessionController.jeton(null));
	}

	@Test
	void us06Rg2MemeReponseQueLeCompteExisteOuNon() {
		when(reinitialisation.demander("jean.dupont@cabinet-test.fr")).thenReturn(Optional.of("jeton"));
		when(reinitialisation.demander("inconnu@test.fr")).thenReturn(Optional.empty());
		assertEquals(new DemandeEnvoyee("Si un compte existe, un email vous a été envoyé", "jeton"),
				motDePasse.demander(new DemandeRequest("jean.dupont@cabinet-test.fr")));
		assertEquals(new DemandeEnvoyee("Si un compte existe, un email vous a été envoyé", null),
				motDePasse.demander(new DemandeRequest("inconnu@test.fr")));
	}

	@Test
	void us06VerificationDuLien() {
		when(reinitialisation.lienValable("bon")).thenReturn(true);
		assertEquals(HttpStatus.NO_CONTENT, motDePasse.verifierLien(new LienRequest("bon")).getStatusCode());
		ResponseEntity<Object> expire = motDePasse.verifierLien(new LienRequest("vieux"));
		assertEquals(HttpStatus.GONE, expire.getStatusCode());
		assertEquals(new ErreurResponse("Ce lien a expiré, demandez-en un nouveau"), expire.getBody());
	}

	@Test
	void us06Reinitialisation() {
		when(reinitialisation.reinitialiser("bon", "Nouveau#Pointeo2026"))
				.thenReturn(ResultatReinitialisation.change());
		when(reinitialisation.reinitialiser("vieux", "Nouveau#Pointeo2026"))
				.thenReturn(ResultatReinitialisation.lienExpire());
		when(reinitialisation.reinitialiser("bon", "bonjour"))
				.thenReturn(ResultatReinitialisation.tropFaible(List.of(RegleMotDePasse.LONGUEUR)));

		assertEquals(HttpStatus.NO_CONTENT, motDePasse
				.reinitialiser(new NouveauMotDePasseRequest("bon", "Nouveau#Pointeo2026")).getStatusCode());
		assertEquals(HttpStatus.GONE, motDePasse
				.reinitialiser(new NouveauMotDePasseRequest("vieux", "Nouveau#Pointeo2026")).getStatusCode());
		ResponseEntity<Object> faible = motDePasse.reinitialiser(new NouveauMotDePasseRequest("bon", "bonjour"));
		assertEquals(HttpStatus.BAD_REQUEST, faible.getStatusCode());
		assertEquals(new ErreurMotDePasse("Le mot de passe ne respecte pas toutes les règles",
				List.of("12 caractères minimum")), faible.getBody());
	}

}
