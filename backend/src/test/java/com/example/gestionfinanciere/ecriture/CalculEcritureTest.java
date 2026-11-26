package com.example.gestionfinanciere.ecriture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

// US-56 RG1 à RG4 : écriture d'une facture d'achat.
class CalculEcritureTest {

	private static final LocalDate EMISE_LE = LocalDate.of(2026, 6, 12);

	private static BigDecimal euros(String montant) {
		return new BigDecimal(montant);
	}

	private static CalculEcriture.Ligne ligne(String compte, String libelle, String debit, String credit) {
		return new CalculEcriture.Ligne(compte, libelle, euros(debit), euros(credit));
	}

	// CA1 : facture EDF 186,40 TTC (HT 155,33, TVA 31,07), contrepartie 607.
	@Test
	void ca1FactureEdf() {
		CalculEcriture.Resultat ecriture = CalculEcriture.achat(new PieceAchat("EDF", "F-2026-0611", EMISE_LE,
				euros("155.33"), List.of(new PieceAchat.TvaDuTaux(euros("20.00"), euros("31.07"))), euros("186.4"),
				"607"));

		assertEquals(EMISE_LE, ecriture.date());
		assertEquals("EDF F-2026-0611", ecriture.libelle());
		assertEquals(List.of(ligne("607", "EDF F-2026-0611", "155.33", "0"),
				ligne("44566", "EDF F-2026-0611 – TVA 20 %", "31.07", "0"),
				ligne("401", "EDF F-2026-0611", "0", "186.40")), ecriture.lignes());
		assertTrue(ecriture.equilibree());
		assertEquals(euros("186.40"), ecriture.totalDebit());
		assertEquals(euros("186.40"), ecriture.totalCredit());
	}

	// RG4 : une ligne de TVA par taux (taux inconnu : « TVA ») ; TVA nulle : pas de ligne.
	@Test
	void rg4PlusieursTaux() {
		CalculEcriture.Resultat ecriture = CalculEcriture.achat(new PieceAchat("Métro", "M-12", EMISE_LE,
				euros("160.00"), List.of(new PieceAchat.TvaDuTaux(euros("5.50"), euros("2.75")),
						new PieceAchat.TvaDuTaux(null, euros("20.00")), new PieceAchat.TvaDuTaux(euros("0"), euros("0"))),
				euros("182.75"), "606"));

		assertEquals(List.of("606", "44566", "44566", "401"),
				ecriture.lignes().stream().map(CalculEcriture.Ligne::numeroCompte).toList());
		assertEquals("Métro M-12 – TVA 5,5 %", ecriture.lignes().get(1).libelle());
		assertEquals("Métro M-12 – TVA", ecriture.lignes().get(2).libelle());
		assertTrue(ecriture.equilibree());
	}

	// Avoir : montants négatifs passés de l'autre côté, toujours équilibré.
	@Test
	void avoir() {
		CalculEcriture.Resultat ecriture = CalculEcriture.achat(new PieceAchat("EDF", "AV-1", EMISE_LE,
				euros("-10.00"), List.of(new PieceAchat.TvaDuTaux(euros("20"), euros("-2.00"))), euros("-12.00"),
				"607"));

		assertEquals(List.of(ligne("607", "EDF AV-1", "0", "10.00"), ligne("44566", "EDF AV-1 – TVA 20 %", "0", "2.00"),
				ligne("401", "EDF AV-1", "12.00", "0")), ecriture.lignes());
		assertTrue(ecriture.equilibree());
	}

	// RG2 : HT + TVA ≠ TTC, l'écriture n'est pas équilibrée.
	@Test
	void rg2Desequilibree() {
		CalculEcriture.Resultat ecriture = CalculEcriture.achat(new PieceAchat("EDF", "F-1", EMISE_LE,
				euros("100"), List.of(new PieceAchat.TvaDuTaux(euros("20"), euros("20"))), euros("130"), "607"));

		assertFalse(ecriture.equilibree());
	}

}
