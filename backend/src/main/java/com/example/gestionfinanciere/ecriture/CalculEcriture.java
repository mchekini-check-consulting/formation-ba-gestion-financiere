package com.example.gestionfinanciere.ecriture;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * US-56 : écriture d'une facture d'achat au journal des achats, sans rien enregistrer.
 * RG1 : débit du compte de contrepartie (HT), débit de la TVA déductible 44566, crédit du
 * fournisseur 401 (TTC). RG3 : datée de l'émission, libellé « tiers numéro ». RG4 : une ligne de TVA
 * par taux. Un montant nul ne donne pas de ligne ; un montant négatif (avoir) passe de l'autre côté.
 */
public final class CalculEcriture {

	public static final String COMPTE_FOURNISSEUR = "401";
	public static final String COMPTE_TVA_DEDUCTIBLE = "44566";

	private CalculEcriture() {
	}

	// Ligne calculée ; montants à 2 décimales, pour comparer avec ceux enregistrés.
	public record Ligne(String numeroCompte, String libelle, BigDecimal debit, BigDecimal credit) {

		public Ligne {
			debit = debit.setScale(2, RoundingMode.HALF_UP);
			credit = credit.setScale(2, RoundingMode.HALF_UP);
		}

	}

	// piece : n° de pièce (US-47 RG1), le n° de la facture.
	public record Resultat(LocalDate date, String piece, String libelle, List<Ligne> lignes) {

		public Resultat {
			lignes = List.copyOf(lignes);
		}

		public BigDecimal totalDebit() {
			return lignes.stream().map(Ligne::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
		}

		public BigDecimal totalCredit() {
			return lignes.stream().map(Ligne::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
		}

		// RG2 : total au débit = total au crédit.
		public boolean equilibree() {
			return totalDebit().compareTo(totalCredit()) == 0;
		}

	}

	public static Resultat achat(PieceAchat piece) {
		String libelle = piece.nomTiers() + " " + piece.numeroFacture();
		List<Ligne> lignes = new ArrayList<>();
		ajouter(lignes, piece.compteContrepartie(), libelle, piece.montantHt(), true);
		for (PieceAchat.TvaDuTaux tva : piece.tva()) {
			String libelleTva = tva.taux() == null ? libelle + " – TVA" : libelle + " – TVA " + taux(tva.taux()) + " %";
			ajouter(lignes, COMPTE_TVA_DEDUCTIBLE, libelleTva, tva.montant(), true);
		}
		ajouter(lignes, COMPTE_FOURNISSEUR, libelle, piece.montantTtc(), false);
		return new Resultat(piece.dateEmission(), piece.numeroFacture(), libelle, lignes);
	}

	private static void ajouter(List<Ligne> lignes, String compte, String libelle, BigDecimal montant,
			boolean auDebit) {
		if (montant == null || montant.signum() == 0) {
			return;
		}
		boolean debit = auDebit == (montant.signum() > 0);
		BigDecimal valeur = montant.abs();
		lignes.add(new Ligne(compte, libelle, debit ? valeur : BigDecimal.ZERO, debit ? BigDecimal.ZERO : valeur));
	}

	// 20.00 -> « 20 » ; 5.50 -> « 5,5 ».
	private static String taux(BigDecimal taux) {
		return taux.stripTrailingZeros().toPlainString().replace('.', ',');
	}

}
