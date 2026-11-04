package com.example.gestionfinanciere.compte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.gestionfinanciere.compte.ResultatSession.Statut;

class SessionServiceTest {

	private final SessionRepository sessions = mock(SessionRepository.class);
	private final HorlogeReglable horloge = new HorlogeReglable(Instant.parse("2026-11-04T09:00:00Z"));
	private final SessionService service = new SessionService(sessions, horloge);
	private final Compte jean = new Compte("jean.dupont@cabinet-test.fr", "x", Role.COLLABORATEUR, true,
			"Jean Dupont", "0612345642");
	private final Map<String, SessionUtilisateur> base = new HashMap<>();

	@BeforeEach
	void base() {
		when(sessions.save(any(SessionUtilisateur.class))).thenAnswer(appel -> {
			SessionUtilisateur session = appel.getArgument(0);
			base.put(session.getEmpreinteJeton(), session);
			return session;
		});
		when(sessions.findByEmpreinteJeton(anyString()))
				.thenAnswer(appel -> Optional.ofNullable(base.get(appel.<String>getArgument(0))));
	}

	@Test
	void sessionOuverteEstActive() {
		String jeton = service.ouvrir(jean);
		ResultatSession resultat = service.consulter(jeton);
		assertEquals(Statut.ACTIVE, resultat.statut());
		assertSame(jean, resultat.compte());
	}

	@Test
	void rg2TrenteMinutesSansActiviteExpirent() {
		String jeton = service.ouvrir(jean);
		horloge.avancer(Duration.ofMinutes(30));
		assertEquals(Statut.EXPIREE, service.consulter(jeton).statut());
		assertEquals(Statut.NON_CONNECTE, service.consulter(jeton).statut());
	}

	@Test
	void chaqueRequeteRepousseLExpiration() {
		String jeton = service.ouvrir(jean);
		horloge.avancer(Duration.ofMinutes(25));
		assertEquals(Statut.ACTIVE, service.consulter(jeton).statut());
		horloge.avancer(Duration.ofMinutes(25));
		assertEquals(Statut.ACTIVE, service.consulter(jeton).statut());
	}

	@Test
	void rg1DeconnexionFermeLaSession() {
		String jeton = service.ouvrir(jean);
		service.fermer(jeton);
		assertEquals(Statut.NON_CONNECTE, service.consulter(jeton).statut());
		service.fermer(null);
	}

	@Test
	void jetonAbsentOuInconnu() {
		assertEquals(Statut.NON_CONNECTE, service.consulter(null).statut());
		assertEquals(Statut.NON_CONNECTE, service.consulter("inconnu").statut());
	}

	@Test
	void fermerToutesFermeLesSessionsDuCompte() {
		String jeton = service.ouvrir(jean);
		SessionUtilisateur session = base.get(Jetons.empreinte(jeton));
		when(sessions.findByCompteAndFermeeFalse(jean)).thenReturn(List.of(session));
		service.fermerToutes(jean);
		assertTrue(session.isFermee());
	}

}
