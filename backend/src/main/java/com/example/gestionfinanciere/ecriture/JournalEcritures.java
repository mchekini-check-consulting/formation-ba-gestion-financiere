package com.example.gestionfinanciere.ecriture;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * US-47 RG1 : journal d'un dossier sur une période (« 2026-06 » : un mois ; « 2026 » : l'exercice).
 * periodes : celles qui ont des écritures, la plus récente d'abord ; periode vide s'il n'y en a aucune.
 */
public record JournalEcritures(String periode, List<Periode> periodes, List<Ligne> ecritures,
		BigDecimal totalDebit, BigDecimal totalCredit) {

	public record Periode(String code, String libelle) {
	}

	// Une écriture du journal ; exportee : en lecture seule (RG2, CA3) ; modifiee* : RG4.
	public record Ligne(Long id, Ecriture.Journal journal, LocalDate date, String piece, String libelle,
			boolean exportee, Instant modifieeLe, String modifieePar, List<VueEcriture.Ligne> lignes,
			BigDecimal totalDebit, BigDecimal totalCredit) {

		static Ligne de(Ecriture ecriture, VueEcriture vue) {
			return new Ligne(ecriture.getId(), vue.journal(), vue.date(), ecriture.getNumeroPiece(), vue.libelle(),
					vue.exportee(), vue.modifieeLe(), vue.modifieePar(), vue.lignes(), vue.totalDebit(),
					vue.totalCredit());
		}

	}

}
