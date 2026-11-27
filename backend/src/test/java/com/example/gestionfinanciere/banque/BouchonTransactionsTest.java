package com.example.gestionfinanciere.banque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.gestionfinanciere.compte.HorlogeReglable;

class BouchonTransactionsTest {

	// 18/11/2026 à 23h30 UTC : déjà le 19/11 à Paris.
	private static final Instant MAINTENANT = Instant.parse("2026-11-18T23:30:00Z");
	private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 19);

	private final BouchonTransactions bouchon = new BouchonTransactions(new HorlogeReglable(MAINTENANT));

	private static InputStream texte(String contenu) {
		return new ByteArrayInputStream(contenu.getBytes(StandardCharsets.UTF_8));
	}

	// Jeu de test : au moins 30 transactions sur les 3 derniers mois, entrées et sorties, source « Bouchon ».
	@Test
	void jeuDeTest() {
		List<Transaction> transactions = bouchon.transactions(null);

		assertTrue(transactions.size() >= 30);
		assertTrue(transactions.stream().allMatch(t -> t.source() == Transaction.Source.BOUCHON));
		assertTrue(transactions.stream().anyMatch(t -> t.sens() == Transaction.Sens.ENTREE));
		assertTrue(transactions.stream().anyMatch(t -> t.sens() == Transaction.Sens.SORTIE));
		assertTrue(transactions.stream().allMatch(t -> !t.date().isAfter(AUJOURDHUI)
				&& !t.date().isBefore(AUJOURDHUI.minusMonths(3))));
		assertTrue(transactions.stream().allMatch(t -> t.montant().signum() > 0));
	}

	// Exemple de rapprochement (US-25) : prélèvement EDF de 186,40 €, il y a 2 jours.
	// US-30 : référence stable = n° de ligne du fichier.
	@Test
	void prelevementEdf() {
		assertTrue(bouchon.transactions(null).contains(new Transaction("bouchon-22", AUJOURDHUI.minusDays(2),
				"PRLV SEPA EDF - Facture electricite octobre", "EDF", new BigDecimal("186.40"),
				Transaction.Sens.SORTIE, Transaction.Source.BOUCHON)));
	}

	// Commentaires, lignes vides et en-tête ignorés ; virgule acceptée pour les centimes.
	@Test
	void lectureDuFichier() {
		List<BouchonTransactions.LigneBouchon> lignes = BouchonTransactions.lire(texte("""
				# commentaire
				il_y_a_jours;libelle;tiers;montant;sens

				0; Vente ;Client A;12,50;entree
				"""));

		assertEquals(List.of(new BouchonTransactions.LigneBouchon(4, 0, "Vente", "Client A", new BigDecimal("12.50"),
				Transaction.Sens.ENTREE)), lignes);
	}

	// Une ligne mal écrite arrête le démarrage, avec son numéro.
	@Test
	void ligneMalEcrite() {
		String entete = "il_y_a_jours;libelle;tiers;montant;sens\n";
		assertEquals("bouchon bancaire, ligne 2 : 5 colonnes attendues séparées par « ; »",
				assertThrows(IllegalArgumentException.class,
						() -> BouchonTransactions.lire(texte(entete + "1;Vente;12.50;ENTREE"))).getMessage());
		assertTrue(assertThrows(IllegalArgumentException.class,
				() -> BouchonTransactions.lire(texte(entete + "1;Vente;A;12.50;CREDIT"))).getMessage()
				.startsWith("bouchon bancaire, ligne 2 : valeur incorrecte"));
		assertTrue(assertThrows(IllegalArgumentException.class,
				() -> BouchonTransactions.lire(texte(entete + "x;Vente;A;12.50;ENTREE"))).getMessage()
				.startsWith("bouchon bancaire, ligne 2 : valeur incorrecte"));
		for (String ligne : List.of("1;Vente;A;-5;ENTREE", "1;Vente;A;0;SORTIE", "-1;Vente;A;5;ENTREE",
				"1; ;A;5;ENTREE")) {
			assertEquals("bouchon bancaire, ligne 2 : nombre de jours positif, montant supérieur à 0 et libellé "
					+ "obligatoires", assertThrows(IllegalArgumentException.class,
							() -> BouchonTransactions.lire(texte(entete + ligne))).getMessage());
		}
	}

	@Test
	void fichierAbsent() {
		assertThrows(IllegalStateException.class, () -> BouchonTransactions.ouvrir("/bouchon/absent.csv"));
	}

}
