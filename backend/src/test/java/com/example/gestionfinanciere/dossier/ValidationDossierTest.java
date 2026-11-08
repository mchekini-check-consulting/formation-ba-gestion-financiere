package com.example.gestionfinanciere.dossier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.gestionfinanciere.cabinet.ValidationInscription;

class ValidationDossierTest {

	@Test
	void formulaireCorrect() {
		assertTrue(ValidationDossier.erreurs(new DemandeDossier("Fleurs Martin EURL", "812 345 679")).isEmpty());
		assertTrue(ValidationDossier.erreurs(new DemandeDossier("Fleurs Martin EURL", "812.345-679")).isEmpty());
	}

	@Test
	void rg1SirenDeNeufChiffres() {
		assertEquals(Map.of("siren", ValidationInscription.SIREN_INVALIDE),
				ValidationDossier.erreurs(new DemandeDossier("Fleurs Martin EURL", "81234567")));
		assertEquals(Map.of("siren", ValidationInscription.SIREN_INVALIDE),
				ValidationDossier.erreurs(new DemandeDossier("Fleurs Martin EURL", "81234567A")));
	}

	@Test
	void raisonSocialeTropLongue() {
		assertEquals(Map.of("raisonSociale", ValidationInscription.TROP_LONG),
				ValidationDossier.erreurs(new DemandeDossier("x".repeat(201), "812345679")));
	}

}
