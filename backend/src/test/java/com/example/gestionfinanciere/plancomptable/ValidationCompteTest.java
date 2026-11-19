package com.example.gestionfinanciere.plancomptable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ValidationCompteTest {

	private static final List<String> GENERAUX = List.of("401", "411", "512", "607");

	private static Map<String, String> erreurs(String numero, String libelle) {
		return ValidationCompte.erreurs(new DemandeCompte(numero, libelle), GENERAUX);
	}

	// CA1 : 401MOULINS « Moulins Bertrand », saisi en minuscules avec un espace.
	@Test
	void sousCompteValide() {
		assertTrue(erreurs("401MOULINS", "Moulins Bertrand").isEmpty());
		assertTrue(erreurs(" 401 moulins ", "Moulins Bertrand").isEmpty());
		assertTrue(erreurs("4111", "Clients France").isEmpty());
		assertEquals("401MOULINS", ValidationCompte.numero(" 401 moulins "));
	}

	// CA2 : 9001 -> « Numéro de compte invalide » ; lettres en tête ou caractères spéciaux aussi.
	@Test
	void ca2NumeroInvalide() {
		assertEquals(Map.of("numero", ValidationCompte.NUMERO_INVALIDE), erreurs("9001", "Test"));
		assertEquals(Map.of("numero", ValidationCompte.NUMERO_INVALIDE), erreurs("ABC", "Test"));
		assertEquals(Map.of("numero", ValidationCompte.NUMERO_INVALIDE), erreurs("401-MOULINS", "Test"));
		assertEquals(Map.of("numero", ValidationCompte.NUMERO_INVALIDE), erreurs("40", "Test"));
	}

	// RG1 : un sous-compte prolonge un compte du plan général (et n'est pas ce compte lui-même).
	@Test
	void sansCompteParent() {
		assertEquals(Map.of("numero", ValidationCompte.NUMERO_SANS_COMPTE_PARENT), erreurs("4021", "Test"));
		assertEquals(Map.of("numero", ValidationCompte.NUMERO_SANS_COMPTE_PARENT), erreurs("401", "Test"));
	}

	@Test
	void champsObligatoires() {
		assertEquals(Map.of("numero", ValidationCompte.NUMERO_OBLIGATOIRE, "libelle",
				ValidationCompte.LIBELLE_OBLIGATOIRE), erreurs(null, "  "));
		assertEquals(Map.of("libelle", ValidationCompte.LIBELLE_OBLIGATOIRE), erreurs("401A", null));
		assertEquals(Map.of("libelle", ValidationCompte.LIBELLE_TROP_LONG), erreurs("401A", "x".repeat(201)));
		assertEquals("", ValidationCompte.libelle(null));
	}

}
