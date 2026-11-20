package com.example.gestionfinanciere.facture.lecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.gestionfinanciere.facture.ChampFacture;

class AnalyseurFactureTest {

	// CA1 : texte de la facture facture-edf-juin.pdf.
	private static final String EDF = """
			EDF
			Facture n° F-2026-0611 du 12/06/2026
			Client : Boulangerie Dupont
			Électricité – mai 2026            155,33 €
			Total HT                          155,33 €
			TVA 20 %                           31,07 €
			Total TTC                         186,40 €
			Prélevé le 15/06/2026
			""";

	private static BigDecimal euros(String montant) {
		return new BigDecimal(montant);
	}

	// CA1 : Fournisseur, EDF, F-2026-0611, 12/06/2026, 155,33 €, 31,07 €, 20 %, 186,40 € et un compte proposé.
	@Test
	void ca1FactureEdf() {
		ResultatLecture lu = AnalyseurFacture.analyser(EDF);

		assertTrue(lu.lisible());
		assertEquals("FOURNISSEUR", lu.typeTiers());
		assertEquals("EDF", lu.nomTiers());
		assertEquals("F-2026-0611", lu.numeroFacture());
		assertEquals(LocalDate.of(2026, 6, 12), lu.dateEmission());
		assertEquals(euros("155.33"), lu.montantHt());
		assertEquals(euros("31.07"), lu.montantTva());
		assertEquals(euros("20.00"), lu.tauxTva());
		assertEquals(euros("186.40"), lu.montantTtc());
		// Compte proposé (énergie), toujours à vérifier par le comptable, comme dans la maquette.
		assertEquals("6061", lu.compteContrepartie());
		assertEquals(Set.of(ChampFacture.COMPTE_CONTREPARTIE), lu.aVerifier());
		assertEquals(Set.of(), lu.aRenseigner());
		assertTrue(lu.aVerifierOuARenseigner());
		// RG4 : chaque champ garde son niveau de confiance.
		assertEquals(9, lu.confiances().size());
		assertEquals(1.0, lu.confiances().get(ChampFacture.TYPE_TIERS));
	}

	// CA2 / RG3 : ticket sans nom de fournisseur lisible : le reste est lu, le tiers est « À renseigner ».
	@Test
	void ca2TicketSansTiers() {
		ResultatLecture lu = AnalyseurFacture.analyser("""
				TICKET N° 004512
				22/06/2026 14:32
				Vis inox x100          12,50
				Colle forte              8,90
				TOTAL HT               17,83
				TVA 20,00 %             3,57
				TOTAL TTC              21,40
				""");

		assertTrue(lu.lisible());
		assertNull(lu.nomTiers());
		assertEquals(Set.of(ChampFacture.NOM_TIERS), lu.aRenseigner());
		assertEquals("004512", lu.numeroFacture());
		assertEquals(LocalDate.of(2026, 6, 22), lu.dateEmission());
		assertEquals(euros("17.83"), lu.montantHt());
		assertEquals(euros("3.57"), lu.montantTva());
		assertEquals(euros("20.00"), lu.tauxTva());
		assertEquals(euros("21.40"), lu.montantTtc());
		// Numéro sans « Facture » et première date venue : lus, mais avec un doute.
		assertTrue(lu.aVerifier().containsAll(List.of(ChampFacture.NUMERO_FACTURE, ChampFacture.DATE_EMISSION)));
		// Aucun mot-clé de dépense : compte par défaut, à vérifier.
		assertEquals("607", lu.compteContrepartie());
	}

	// CA3 / RG5 : HT 100,00 + TVA 20,00 ≠ TTC 130,00 -> les trois montants « À vérifier ».
	@Test
	void ca3MontantsIncoherents() {
		ResultatLecture lu = AnalyseurFacture.analyser("""
				Orange
				Facture n° FA-778 du 03/05/2026
				Total HT 100,00 €
				TVA 20 % 20,00 €
				Total TTC 130,00 €
				""");

		assertTrue(lu.aVerifier().containsAll(
				List.of(ChampFacture.MONTANT_HT, ChampFacture.MONTANT_TVA, ChampFacture.MONTANT_TTC)));
		assertEquals("626", lu.compteContrepartie());
	}

	// CA4 : photo illisible (aucun texte, ou rien qui ressemble à un montant) -> « Lecture échouée ».
	@Test
	void ca4LectureImpossible() {
		for (String texte : new String[] { null, "", "   \n ", "~~ ;; ##\nIl1| oO0" }) {
			ResultatLecture lu = AnalyseurFacture.analyser(texte);
			assertFalse(lu.lisible());
			assertEquals(Set.of(), lu.aRenseigner());
			assertFalse(lu.aVerifierOuARenseigner());
			assertNull(lu.montantTtc());
		}
	}

	// RG3 : tiers annoncé sur la facture mais inconnu : gardé tel qu'écrit, avec un léger doute.
	@Test
	void tiersAnnonce() {
		ResultatLecture lu = AnalyseurFacture.analyser("""
				Fournisseur : Ébénisterie Morel
				Facture N° 2026-15 émise le 3 mars 2026
				Montant HT : 1 200,00 €
				TVA : 240,00 €
				Net à payer : 1 440,00 €
				""");

		assertEquals("Ébénisterie Morel", lu.nomTiers());
		assertEquals("2026-15", lu.numeroFacture());
		assertEquals(LocalDate.of(2026, 3, 3), lu.dateEmission());
		assertEquals(euros("1200.00"), lu.montantHt());
		assertEquals(euros("1440.00"), lu.montantTtc());
		// Taux absent : calculé depuis HT et TVA (20 %), à vérifier.
		assertEquals(euros("20.00"), lu.tauxTva());
		assertTrue(lu.aVerifier().contains(ChampFacture.TAUX_TVA));
		assertFalse(lu.aVerifier().contains(ChampFacture.NOM_TIERS));
	}

	// Ticket minimal : seul un total, sans HT ni TVA ; un seul montant : pris comme TTC, à vérifier.
	@Test
	void ticketMinimal() {
		ResultatLecture total = AnalyseurFacture.analyser("Boulangerie\nTOTAL 12,50\n");
		assertEquals(euros("12.50"), total.montantTtc());
		assertTrue(total.aVerifier().contains(ChampFacture.MONTANT_TTC));
		assertTrue(total.aRenseigner().containsAll(List.of(ChampFacture.MONTANT_HT, ChampFacture.MONTANT_TVA,
				ChampFacture.TAUX_TVA, ChampFacture.NOM_TIERS)));

		ResultatLecture seul = AnalyseurFacture.analyser("Merci de votre visite\n4,20 3,10\n");
		assertEquals(euros("4.20"), seul.montantTtc());
		assertTrue(seul.aVerifier().contains(ChampFacture.MONTANT_TTC));
	}

	// Taux affiché hors de la ligne TVA ; taux calculé hors des taux connus : non proposé.
	@Test
	void tauxDeTva() {
		ResultatLecture ailleurs = AnalyseurFacture.analyser("Remise 5 %\nTotal HT 10,00\nTotal TTC 11,00\n");
		assertEquals(euros("5.00"), ailleurs.tauxTva());
		assertTrue(ailleurs.aVerifier().contains(ChampFacture.TAUX_TVA));

		ResultatLecture inconnu = AnalyseurFacture.analyser("Total HT 100,00\nMontant TVA 33,00\n");
		assertNull(inconnu.tauxTva());
		assertTrue(inconnu.aRenseigner().contains(ChampFacture.TAUX_TVA));
	}

	@Test
	void montantsEtDates() {
		assertEquals(List.of(euros("1234.56"), euros("1234.56"), euros("186.40")),
				AnalyseurFacture.montantsDe("1 234,56 et 1.234,56 puis 186.40 mais pas 5,50 % ni 12.06.2026"));
		assertEquals(Optional.of(LocalDate.of(2026, 6, 12)), AnalyseurFacture.dateDe("le 12-06-26"));
		assertEquals(Optional.of(LocalDate.of(2026, 8, 1)), AnalyseurFacture.dateDe("1er aout 2026"));
		assertEquals(Optional.empty(), AnalyseurFacture.dateDe("le 31/02/2026 ou 45/13/2026"));
		assertEquals("electricite", AnalyseurFacture.sansAccents("électricité"));
	}

	@Test
	void resultatIllisibleEtCopies() {
		ResultatLecture illisible = ResultatLecture.illisible();
		assertFalse(illisible.lisible());
		assertTrue(illisible.confiances().isEmpty());
		assertTrue(illisible.aVerifier().isEmpty());
	}

}
