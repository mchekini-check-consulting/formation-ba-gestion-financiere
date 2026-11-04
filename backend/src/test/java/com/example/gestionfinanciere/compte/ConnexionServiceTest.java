package com.example.gestionfinanciere.compte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.gestionfinanciere.compte.ResultatConnexion.Statut;
import com.example.gestionfinanciere.notification.EnvoiEmail;

class ConnexionServiceTest {

	private static final String EMAIL = "jean.dupont@cabinet-test.fr";
	private static final String MOT_DE_PASSE = "Bon-mot-de-passe1";

	// Coût BCrypt minimal : mêmes règles, tests rapides.
	private final PasswordEncoder encodeur = new BCryptPasswordEncoder(4);
	private final CompteRepository comptes = mock(CompteRepository.class);
	private final EnvoiEmail emails = mock(EnvoiEmail.class);
	private final HorlogeReglable horloge = new HorlogeReglable(Instant.parse("2026-11-04T09:00:00Z"));
	private final ConnexionService service = new ConnexionService(comptes, encodeur, horloge, emails,
			"https://frontend.test");

	private Compte compte(Role role, boolean actif) {
		Compte compte = new Compte(EMAIL, encodeur.encode(MOT_DE_PASSE), role, actif, "Jean Dupont", "0612345642");
		when(comptes.findByEmail(EMAIL)).thenReturn(Optional.of(compte));
		return compte;
	}

	private ResultatConnexion mauvaisMotDePasse() {
		return service.authentifier(EMAIL, "mauvais");
	}

	@Test
	void us03Ca1BonsIdentifiantsRenvoieLeCompte() {
		Compte compte = compte(Role.COLLABORATEUR, true);
		ResultatConnexion resultat = service.authentifier(EMAIL, MOT_DE_PASSE);
		assertEquals(Statut.REUSSIE, resultat.statut());
		assertSame(compte, resultat.compte());
	}

	@Test
	void us03Rg1LesMajusculesEtEspacesDeLEmailNeComptentPas() {
		compte(Role.DIRIGEANT, true);
		assertEquals(Statut.REUSSIE, service.authentifier("  Jean.Dupont@Cabinet-Test.FR ", MOT_DE_PASSE).statut());
	}

	@Test
	void us03Ca2MauvaisMotDePasseRefuse() {
		compte(Role.COLLABORATEUR, true);
		ResultatConnexion resultat = mauvaisMotDePasse();
		assertEquals(Statut.INCORRECTE, resultat.statut());
		assertNull(resultat.compte());
	}

	@Test
	void leMotDePasseResteSensibleALaCasse() {
		compte(Role.COLLABORATEUR, true);
		assertEquals(Statut.INCORRECTE, service.authentifier(EMAIL, MOT_DE_PASSE.toUpperCase()).statut());
	}

	@Test
	void motDePasseTropLongRefuseSansErreur() {
		compte(Role.COLLABORATEUR, true);
		assertEquals(Statut.INCORRECTE, service.authentifier(EMAIL, "x".repeat(100)).statut());
	}

	@Test
	void emailInconnuRefuse() {
		when(comptes.findByEmail(anyString())).thenReturn(Optional.empty());
		assertEquals(Statut.INCORRECTE, service.authentifier("inconnu@test.fr", MOT_DE_PASSE).statut());
	}

	@Test
	void compteInactifRefuse() {
		compte(Role.COLLABORATEUR, false);
		assertEquals(Statut.INCORRECTE, service.authentifier(EMAIL, MOT_DE_PASSE).statut());
	}

	@Test
	void champsVidesRefusesSansInterrogerLaBase() {
		assertEquals(Statut.INCORRECTE, service.authentifier(null, MOT_DE_PASSE).statut());
		assertEquals(Statut.INCORRECTE, service.authentifier("  ", MOT_DE_PASSE).statut());
		assertEquals(Statut.INCORRECTE, service.authentifier(EMAIL, null).statut());
		assertEquals(Statut.INCORRECTE, service.authentifier(EMAIL, "").statut());
		verify(comptes, never()).findByEmail(anyString());
	}

	@Test
	void us05Ca1TroisiemeMauvaisMotDePasseBloqueEtPrevientParEmail() {
		Compte compte = compte(Role.COLLABORATEUR, true);
		assertEquals(Statut.INCORRECTE, mauvaisMotDePasse().statut());
		assertEquals(Statut.INCORRECTE, mauvaisMotDePasse().statut());
		verify(emails, never()).envoyer(anyString(), anyString(), anyString());

		assertEquals(Statut.BLOQUEE, mauvaisMotDePasse().statut());
		assertEquals(Instant.parse("2026-11-04T09:15:00Z"), compte.getBloqueJusqua());
		verify(emails).envoyer(eq(EMAIL), contains("bloqué 15 minutes"),
				contains("https://frontend.test/mot-de-passe-oublie"));
	}

	@Test
	void us05PendantLeBlocageMemeLeBonMotDePasseEstRefuse() {
		compte(Role.COLLABORATEUR, true);
		mauvaisMotDePasse();
		mauvaisMotDePasse();
		mauvaisMotDePasse();
		horloge.avancer(Duration.ofMinutes(14));
		assertEquals(Statut.BLOQUEE, service.authentifier(EMAIL, MOT_DE_PASSE).statut());
	}

	@Test
	void us05Ca2ApresSeizeMinutesLaConnexionFonctionne() {
		Compte compte = compte(Role.COLLABORATEUR, true);
		mauvaisMotDePasse();
		mauvaisMotDePasse();
		mauvaisMotDePasse();
		horloge.avancer(Duration.ofMinutes(16));
		assertEquals(Statut.REUSSIE, service.authentifier(EMAIL, MOT_DE_PASSE).statut());
		assertNull(compte.getBloqueJusqua());
	}

	@Test
	void us05Rg3UneConnexionReussieRemetLeCompteurAZero() {
		Compte compte = compte(Role.COLLABORATEUR, true);
		mauvaisMotDePasse();
		mauvaisMotDePasse();
		service.authentifier(EMAIL, MOT_DE_PASSE);
		assertEquals(0, compte.getEchecsConsecutifs());
		assertEquals(Statut.INCORRECTE, mauvaisMotDePasse().statut());
		assertEquals(Statut.INCORRECTE, mauvaisMotDePasse().statut());
	}

	@Test
	void normaliserEtEstVide() {
		assertEquals(EMAIL, ConnexionService.normaliser(" JEAN.DUPONT@cabinet-test.fr "));
		assertTrue(ConnexionService.estVide(" "));
	}

}
