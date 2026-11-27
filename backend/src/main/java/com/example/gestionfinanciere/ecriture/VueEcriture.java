package com.example.gestionfinanciere.ecriture;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * US-56 RG6 : écriture affichée dans le bloc « Écritures générées » de la fiche facture.
 * exportee : « Re-générer les écritures » désactivé (CA3). aJour : faux si la facture a été corrigée
 * depuis la génération ou depuis la correction à la main (« À re-générer », RG5).
 * US-47 RG4 : modifieePar, modifieeLe : dernière correction à la main, vides sinon.
 */
public record VueEcriture(Ecriture.Journal journal, LocalDate date, String libelle, Instant genereeLe,
		String genereePar, Instant modifieeLe, String modifieePar, boolean exportee, boolean aJour,
		List<Ligne> lignes, BigDecimal totalDebit, BigDecimal totalCredit) {

	// libelleCompte : libellé du compte dans le plan comptable du dossier.
	public record Ligne(String numeroCompte, String libelleCompte, String libelle, BigDecimal debit,
			BigDecimal credit) {
	}

}
