package com.example.gestionfinanciere.compte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class ConnexionServiceTest {

	private static final String EMAIL = "jean.dupont@cabinet-test.fr";
	private static final String MOT_DE_PASSE = "Bon-mot-de-passe1";

	// Coût BCrypt minimal : mêmes règles, tests rapides.
	private final PasswordEncoder encodeur = new BCryptPasswordEncoder(4);
	private final CompteRepository comptes = mock(CompteRepository.class);
	private final ConnexionService service = new ConnexionService(comptes, encodeur);

	private Compte compte(Role role, boolean actif) {
		return new Compte(EMAIL, encodeur.encode(MOT_DE_PASSE), role, actif);
	}

	@Test
	void ca1BonsIdentifiantsRenvoieLeRole() {
		when(comptes.findByEmail(EMAIL)).thenReturn(Optional.of(compte(Role.COLLABORATEUR, true)));
		assertEquals(Optional.of(Role.COLLABORATEUR), service.authentifier(EMAIL, MOT_DE_PASSE));
	}

	@Test
	void dirigeantRenvoieSonRole() {
		when(comptes.findByEmail(EMAIL)).thenReturn(Optional.of(compte(Role.DIRIGEANT, true)));
		assertEquals(Optional.of(Role.DIRIGEANT), service.authentifier(EMAIL, MOT_DE_PASSE));
	}

	@Test
	void rg1LesMajusculesEtEspacesDeLEmailNeComptentPas() {
		when(comptes.findByEmail(EMAIL)).thenReturn(Optional.of(compte(Role.COLLABORATEUR, true)));
		assertEquals(Optional.of(Role.COLLABORATEUR),
				service.authentifier("  Jean.Dupont@Cabinet-Test.FR ", MOT_DE_PASSE));
	}

	@Test
	void ca2MauvaisMotDePasseRefuse() {
		when(comptes.findByEmail(EMAIL)).thenReturn(Optional.of(compte(Role.COLLABORATEUR, true)));
		assertTrue(service.authentifier(EMAIL, "mauvais").isEmpty());
	}

	@Test
	void leMotDePasseResteSensibleALaCasse() {
		when(comptes.findByEmail(EMAIL)).thenReturn(Optional.of(compte(Role.COLLABORATEUR, true)));
		assertTrue(service.authentifier(EMAIL, MOT_DE_PASSE.toUpperCase()).isEmpty());
	}

	@Test
	void emailInconnuRefuse() {
		when(comptes.findByEmail(anyString())).thenReturn(Optional.empty());
		assertTrue(service.authentifier("inconnu@test.fr", MOT_DE_PASSE).isEmpty());
	}

	@Test
	void compteInactifRefuse() {
		when(comptes.findByEmail(EMAIL)).thenReturn(Optional.of(compte(Role.COLLABORATEUR, false)));
		assertTrue(service.authentifier(EMAIL, MOT_DE_PASSE).isEmpty());
	}

	@Test
	void champsVidesRefusesSansInterrogerLaBase() {
		assertTrue(service.authentifier(null, MOT_DE_PASSE).isEmpty());
		assertTrue(service.authentifier("  ", MOT_DE_PASSE).isEmpty());
		assertTrue(service.authentifier(EMAIL, null).isEmpty());
		assertTrue(service.authentifier(EMAIL, "").isEmpty());
		verify(comptes, never()).findByEmail(anyString());
	}

	@Test
	void constructeurParDefautUtiliseBCrypt() {
		ConnexionService parDefaut = new ConnexionService(comptes);
		when(comptes.findByEmail(EMAIL)).thenReturn(Optional.of(
				new Compte(EMAIL, new BCryptPasswordEncoder().encode(MOT_DE_PASSE), Role.DIRIGEANT, true)));
		assertEquals(Optional.of(Role.DIRIGEANT), parDefaut.authentifier(EMAIL, MOT_DE_PASSE));
	}

	@Test
	void normaliserMetEnMinusculesEtRetireLesEspaces() {
		assertEquals(EMAIL, ConnexionService.normaliser(" JEAN.DUPONT@cabinet-test.fr "));
	}

}
