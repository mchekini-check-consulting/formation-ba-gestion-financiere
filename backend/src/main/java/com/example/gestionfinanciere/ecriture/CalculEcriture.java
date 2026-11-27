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
 * US-57 : facture de vente, même calcul au sens inversé (journal des ventes) : débit du client 411
 * (TTC), crédit du compte de produit (HT), crédit de la TVA collectée 44571.
 */
public final class CalculEcriture {

	public static final String COMPTE_FOURNISSEUR = "401";
	public static final String COMPTE_TVA_DEDUCTIBLE = "44566";
	public static final String COMPTE_CLIENT = "411";
	public static final String COMPTE_TVA_COLLECTEE = "44571";
	public static final String COMPTE_BANQUE = "512";
	private static final BigDecimal CENT = BigDecimal.valueOf(100);

	private CalculEcriture() {
	}

	// Ligne calculée ; montants à 2 décimales, pour comparer avec ceux enregistrés.
	public record Ligne(String numeroCompte, String libelle, BigDecimal debit, BigDecimal credit) {

		public Ligne {
			debit = debit.setScale(2, RoundingMode.HALF_UP);
			credit = credit.setScale(2, RoundingMode.HALF_UP);
		}

	}

	public record Resultat(LocalDate date, String libelle, List<Ligne> lignes) {

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

	// US-56 RG1 : contrepartie et TVA déductible au débit, fournisseur 401 au crédit.
	public static Resultat achat(PieceAchat piece) {
		String libelle = libelle(piece);
		List<Ligne> lignes = new ArrayList<>();
		ajouter(lignes, piece.compteContrepartie(), libelle, piece.montantHt(), true);
		ajouterTva(lignes, piece, COMPTE_TVA_DEDUCTIBLE, true);
		ajouter(lignes, COMPTE_FOURNISSEUR, libelle, piece.montantTtc(), false);
		return new Resultat(piece.dateEmission(), libelle, lignes);
	}

	// US-57 RG1 : client 411 au débit, produit et TVA collectée au crédit.
	public static Resultat vente(PieceAchat piece) {
		String libelle = libelle(piece);
		List<Ligne> lignes = new ArrayList<>();
		ajouter(lignes, COMPTE_CLIENT, libelle, piece.montantTtc(), true);
		ajouter(lignes, piece.compteContrepartie(), libelle, piece.montantHt(), false);
		ajouterTva(lignes, piece, COMPTE_TVA_COLLECTEE, false);
		return new Resultat(piece.dateEmission(), libelle, lignes);
	}

	/**
	 * US-30 RG1 : écriture d'une opération bancaire validée, libellé de l'opération, datée de
	 * l'opération. Le montant (TTC) est réparti en HT et TVA au taux donné (HT arrondi au centime,
	 * TVA = TTC - HT : toujours équilibrée, RG2). Sortie d'argent : compte de la catégorie et TVA
	 * au débit, banque 512 au crédit ; entrée d'argent : l'inverse.
	 */
	public static Resultat operation(LocalDate date, String libelle, BigDecimal montant, boolean sortie,
			String compte, String compteTva, BigDecimal tauxTva) {
		BigDecimal ttc = montant.setScale(2, RoundingMode.HALF_UP);
		BigDecimal ht = ttc.multiply(CENT).divide(CENT.add(tauxTva), 2, RoundingMode.HALF_UP);
		BigDecimal tva = ttc.subtract(ht);
		String libelleTva = libelle + " – TVA " + taux(tauxTva) + " %";
		List<Ligne> lignes = new ArrayList<>();
		if (sortie) {
			ajouter(lignes, compte, libelle, ht, true);
			ajouter(lignes, compteTva, libelleTva, tva, true);
			ajouter(lignes, COMPTE_BANQUE, libelle, ttc, false);
		} else {
			ajouter(lignes, COMPTE_BANQUE, libelle, ttc, true);
			ajouter(lignes, compte, libelle, ht, false);
			ajouter(lignes, compteTva, libelleTva, tva, false);
		}
		return new Resultat(date, libelle, lignes);
	}

	private static String libelle(PieceAchat piece) {
		return piece.nomTiers() + " " + piece.numeroFacture();
	}

	// RG4 : une ligne de TVA par taux.
	private static void ajouterTva(List<Ligne> lignes, PieceAchat piece, String compte, boolean auDebit) {
		String libelle = libelle(piece);
		for (PieceAchat.TvaDuTaux tva : piece.tva()) {
			String libelleTva = tva.taux() == null ? libelle + " – TVA" : libelle + " – TVA " + taux(tva.taux()) + " %";
			ajouter(lignes, compte, libelleTva, tva.montant(), auDebit);
		}
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
