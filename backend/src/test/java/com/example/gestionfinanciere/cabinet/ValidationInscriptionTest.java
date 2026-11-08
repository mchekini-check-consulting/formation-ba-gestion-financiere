package com.example.gestionfinanciere.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ValidationInscriptionTest {

	// CA1 : formulaire de Claire Martin pour Cabinet Test.
	static DemandeInscription claire() {
		return new DemandeInscription("Cabinet Test", "123 456 789", "Claire", "Martin",
				"Claire.Martin@Cabinet-Test.fr", "Pointeo2026!!", 120, null, true);
	}

	private static Map<String, String> erreurs(DemandeInscription demande) {
		return ValidationInscription.erreurs(demande);
	}

	@Test
	void formulaireCompletSansErreur() {
		assertTrue(erreurs(claire()).isEmpty());
	}

	@Test
	void formulaireRemisEnForme() {
		FormulaireValide f = ValidationInscription.remettreEnForme(new DemandeInscription(" Cabinet Test ",
				"123 456 789", " Claire ", " Martin ", " Claire.Martin@Cabinet-Test.FR ",
				"Pointeo2026!!", 120, " test-4821 ", true));
		assertEquals(new FormulaireValide("Cabinet Test", "123456789", "Claire", "Martin",
				"claire.martin@cabinet-test.fr", "Pointeo2026!!", 120, "TEST-4821"), f);
		assertNull(ValidationInscription.remettreEnForme(claire()).codeParrainage());
	}

	@Test
	void champsObligatoires() {
		Map<String, String> e = erreurs(
				new DemandeInscription(" ", null, "", null, " ", null, null, null, null));
		assertEquals(Map.of("nomCabinet", ValidationInscription.OBLIGATOIRE, "siren", ValidationInscription.OBLIGATOIRE,
				"prenom", ValidationInscription.OBLIGATOIRE, "nom", ValidationInscription.OBLIGATOIRE, "email",
				ValidationInscription.OBLIGATOIRE, "motDePasse",
				ValidationInscription.OBLIGATOIRE, "nombreDossiers", ValidationInscription.OBLIGATOIRE,
				"cguAcceptees", ValidationInscription.CGU_NON_ACCEPTEES), e);
	}

	@Test
	void valeursInvalides() {
		Map<String, String> e = erreurs(new DemandeInscription("C".repeat(201), "12345678", "P".repeat(101),
				"Martin", "claire.martin", "faible", 0, "X".repeat(21), false));
		assertEquals(ValidationInscription.TROP_LONG, e.get("nomCabinet"));
		assertEquals(ValidationInscription.SIREN_INVALIDE, e.get("siren"));
		assertEquals(ValidationInscription.TROP_LONG, e.get("prenom"));
		assertEquals(ValidationInscription.EMAIL_INVALIDE, e.get("email"));
		assertEquals(ValidationInscription.MOT_DE_PASSE_FAIBLE, e.get("motDePasse"));
		assertEquals(ValidationInscription.DOSSIERS_INVALIDE, e.get("nombreDossiers"));
		assertEquals(ValidationInscription.PARRAINAGE_INVALIDE, e.get("codeParrainage"));
		assertEquals(ValidationInscription.CGU_NON_ACCEPTEES, e.get("cguAcceptees"));
		assertEquals(8, e.size());
	}

	@Test
	void sirenAvecLettresRefuse() {
		DemandeInscription d = claire();
		assertEquals(ValidationInscription.SIREN_INVALIDE, erreurs(new DemandeInscription(d.nomCabinet(),
				"12345678A", d.prenom(), d.nom(), d.email(), d.motDePasse(), d.nombreDossiers(), null,
				true)).get("siren"));
	}

	@Test
	void tropDeDossiersEtEmailTropLong() {
		DemandeInscription d = claire();
		Map<String, String> e = erreurs(new DemandeInscription(d.nomCabinet(), d.siren(), d.prenom(), d.nom(),
				"a".repeat(250) + "@x.fr", d.motDePasse(), ValidationInscription.DOSSIERS_MAX + 1, "",
				true));
		assertEquals(ValidationInscription.EMAIL_INVALIDE, e.get("email"));
		assertEquals(ValidationInscription.DOSSIERS_INVALIDE, e.get("nombreDossiers"));
		assertEquals(2, e.size());
	}

	@Test
	void sirenAvecSeparateursAccepte() {
		assertEquals("123456789", ValidationInscription.siren("123.456.789"));
	}

}
