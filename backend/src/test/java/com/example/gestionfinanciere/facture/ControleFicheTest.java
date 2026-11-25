package com.example.gestionfinanciere.facture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ControleFicheTest {

	// Facture EDF du CA1, TTC corrigé : complète et cohérente.
	private static InformationsFacture edf() {
		return new InformationsFacture("FOURNISSEUR", "EDF", "F-2026-0611", LocalDate.of(2026, 6, 12),
				new BigDecimal("155.33"), new BigDecimal("31.07"), new BigDecimal("20.00"), new BigDecimal("186.40"),
				"606");
	}

	private static InformationsFacture avec(String typeTiers, String nom, String numero, LocalDate date,
			BigDecimal ht, BigDecimal tva, BigDecimal taux, BigDecimal ttc, String compte) {
		return new InformationsFacture(typeTiers, nom, numero, date, ht, tva, taux, ttc, compte);
	}

	@Test
	void ficheCompleteEtCoherente() {
		assertEquals(Optional.empty(), ControleFiche.format(edf()));
		assertEquals(Optional.empty(), ControleFiche.obligatoires(edf()));
		assertEquals(Optional.empty(), ControleFiche.coherence(edf()));
		// Champs vides : enregistrables (RG2), pas encore validables (RG5).
		InformationsFacture vide = avec(null, null, null, null, null, null, null, null, null);
		assertEquals(Optional.empty(), ControleFiche.format(vide));
		assertEquals(Optional.of("Indiquez le tiers"), ControleFiche.obligatoires(vide));
	}

	// RG5 / CA2 : le premier champ obligatoire manquant est nommé ; type de tiers et taux facultatifs.
	@Test
	void rg5ChampsObligatoires() {
		InformationsFacture e = edf();
		assertEquals(Optional.of("Indiquez le numéro de facture"), ControleFiche.obligatoires(avec(e.typeTiers(),
				e.nomTiers(), null, e.dateEmission(), e.montantHt(), e.montantTva(), e.tauxTva(), e.montantTtc(),
				e.compteContrepartie())));
		List<InformationsFacture> manquants = List.of(
				avec(null, "EDF", "F1", null, e.montantHt(), e.montantTva(), null, e.montantTtc(), "606"),
				avec(null, "EDF", "F1", e.dateEmission(), null, e.montantTva(), null, e.montantTtc(), "606"),
				avec(null, "EDF", "F1", e.dateEmission(), e.montantHt(), null, null, e.montantTtc(), "606"),
				avec(null, "EDF", "F1", e.dateEmission(), e.montantHt(), e.montantTva(), null, null, "606"),
				avec(null, "EDF", "F1", e.dateEmission(), e.montantHt(), e.montantTva(), null, e.montantTtc(), null),
				avec(null, "EDF", "F1", e.dateEmission(), e.montantHt(), e.montantTva(), null, e.montantTtc(), "606"));
		assertEquals(List.of(Optional.of("Indiquez la date d'émission"), Optional.of("Indiquez le montant HT"),
				Optional.of("Indiquez le montant de TVA"), Optional.of("Indiquez le montant TTC"),
				Optional.of("Indiquez le compte de contrepartie"), Optional.empty()),
				manquants.stream().map(ControleFiche::obligatoires).toList());
	}

	// RG4 / CA3 : HT 155,33 + TVA 31,07 ≠ TTC 190,00 ; au centime près, quelle que soit l'écriture.
	@Test
	void rg4Coherence() {
		InformationsFacture e = edf();
		assertEquals(Optional.of("Montants incohérents : HT + TVA ≠ TTC"),
				ControleFiche.coherence(avec(null, "EDF", "F1", e.dateEmission(), e.montantHt(), e.montantTva(), null,
						new BigDecimal("190.00"), "606")));
		assertEquals(Optional.of("Montants incohérents : HT + TVA ≠ TTC"),
				ControleFiche.coherence(avec(null, "EDF", "F1", e.dateEmission(), e.montantHt(), e.montantTva(), null,
						new BigDecimal("186.00"), "606")));
		assertEquals(Optional.empty(), ControleFiche.coherence(avec(null, "EDF", "F1", e.dateEmission(),
				new BigDecimal("155.330"), e.montantTva(), null, new BigDecimal("186.4"), "606")));
	}

	// Valeurs impossibles à enregistrer.
	@Test
	void valeursImpossibles() {
		InformationsFacture e = edf();
		assertEquals(Optional.of("Type de tiers inconnu : choisissez Fournisseur ou Client"),
				ControleFiche.format(avec("AUTRE", null, null, null, null, null, null, null, null)));
		assertEquals(Optional.of("Nom du tiers trop long : 255 caractères maximum"),
				ControleFiche.format(avec(null, "x".repeat(256), null, null, null, null, null, null, null)));
		assertEquals(Optional.of("Numéro de facture trop long : 100 caractères maximum"),
				ControleFiche.format(avec(null, null, "1".repeat(101), null, null, null, null, null, null)));
		assertEquals(Optional.of("Compte de contrepartie trop long : 20 caractères maximum"),
				ControleFiche.format(avec(null, null, null, null, null, null, null, null, "6".repeat(21))));
		assertEquals(Optional.of("Montant HT : 2 décimales au maximum"), ControleFiche
				.format(avec(null, null, null, null, new BigDecimal("10.555"), null, null, null, null)));
		assertEquals(Optional.of("Montant TVA : montant trop grand"), ControleFiche
				.format(avec(null, null, null, null, null, new BigDecimal("10000000000"), null, null, null)));
		assertEquals(Optional.of("Montant TTC : 2 décimales au maximum"), ControleFiche
				.format(avec(null, null, null, null, null, null, null, new BigDecimal("0.001"), null)));
		assertEquals(Optional.of("Taux de TVA : entre 0 et 100 %"), ControleFiche
				.format(avec(null, null, null, null, null, null, new BigDecimal("-1"), null, null)));
		assertEquals(Optional.of("Taux de TVA : entre 0 et 100 %"), ControleFiche
				.format(avec(null, null, null, null, null, null, new BigDecimal("100.01"), null, null)));
		assertEquals(Optional.of("Taux de TVA : 2 décimales au maximum"), ControleFiche
				.format(avec(null, null, null, null, null, null, new BigDecimal("5.555"), null, null)));
		assertEquals(Optional.empty(), ControleFiche.format(avec("CLIENT", e.nomTiers(), e.numeroFacture(), null,
				new BigDecimal("-12.50"), null, BigDecimal.ZERO, null, null)));
	}

	// Espaces retirés, texte vide = champ vide, type de tiers et compte en majuscules.
	@Test
	void nettoyage() {
		InformationsFacture saisie = avec(" fournisseur ", "  EDF  ", "   ", null, null, null, null, null, " 401moulins ");
		assertEquals(avec("FOURNISSEUR", "EDF", null, null, null, null, null, null, "401MOULINS"), saisie.nettoyee());
		assertEquals(avec(null, null, null, null, null, null, null, null, null),
				avec(null, " ", "", null, null, null, null, null, "  ").nettoyee());
	}

	// RG8 : seuls les champs qui changent ; 186.4 et 186.40 sont le même montant.
	@Test
	void rg8Changements() {
		InformationsFacture lue = avec("FOURNISSEUR", "EDF", "F-2026-0611", LocalDate.of(2026, 6, 12),
				new BigDecimal("155.33"), new BigDecimal("31.07"), new BigDecimal("20.00"), new BigDecimal("186.00"),
				"6061");
		assertEquals(List.of(), edf().changementsVers(avec("FOURNISSEUR", "EDF", "F-2026-0611",
				LocalDate.of(2026, 6, 12), new BigDecimal("155.330"), new BigDecimal("31.07"), new BigDecimal("20"),
				new BigDecimal("186.4"), "606")));
		assertEquals(List.of(new InformationsFacture.Changement(ChampFacture.MONTANT_TTC, "186", "186.4"),
				new InformationsFacture.Changement(ChampFacture.COMPTE_CONTREPARTIE, "6061", "606")),
				lue.changementsVers(edf()));
		assertEquals(List.of(new InformationsFacture.Changement(ChampFacture.TYPE_TIERS, null, "CLIENT"),
				new InformationsFacture.Changement(ChampFacture.NOM_TIERS, null, "Martin"),
				new InformationsFacture.Changement(ChampFacture.NUMERO_FACTURE, null, "42"),
				new InformationsFacture.Changement(ChampFacture.DATE_EMISSION, null, "2026-11-02"),
				new InformationsFacture.Changement(ChampFacture.MONTANT_HT, null, "100"),
				new InformationsFacture.Changement(ChampFacture.MONTANT_TVA, null, "20"),
				new InformationsFacture.Changement(ChampFacture.TAUX_TVA, null, "20"),
				new InformationsFacture.Changement(ChampFacture.MONTANT_TTC, null, "120"),
				new InformationsFacture.Changement(ChampFacture.COMPTE_CONTREPARTIE, null, "706")),
				avec(null, null, null, null, null, null, null, null, null)
						.changementsVers(avec("CLIENT", "Martin", "42", LocalDate.of(2026, 11, 2),
								new BigDecimal("100.00"), new BigDecimal("20.00"), new BigDecimal("20"),
								new BigDecimal("120.00"), "706")));
	}

}
