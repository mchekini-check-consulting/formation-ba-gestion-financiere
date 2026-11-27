package com.example.gestionfinanciere.banque;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.example.gestionfinanciere.ecriture.VueEcriture;

/**
 * US-45, US-58 : transaction affichée dans l'onglet Banque ; US-30 : validation est vide tant que
 * l'opération n'est pas validée, sinon sa catégorie, son compte, son taux de TVA et son écriture.
 */
public record VueTransaction(String reference, LocalDate date, String libelle, String tiers, BigDecimal montant,
		Transaction.Sens sens, Transaction.Source source, Validation validation) {

	public record Validation(String categorie, String numeroCompte, BigDecimal tauxTva, String valideePar,
			Instant valideeLe, VueEcriture ecriture) {
	}

	static VueTransaction de(Transaction t, Validation validation) {
		return new VueTransaction(t.reference(), t.date(), t.libelle(), t.tiers(), t.montant(), t.sens(), t.source(),
				validation);
	}

}
