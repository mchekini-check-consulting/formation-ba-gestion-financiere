package com.example.gestionfinanciere.banque;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * US-58 Transaction bancaire d'un dossier, au même format quelle que soit son origine : le bouchon
 * (données de démonstration) ou, plus tard, la connexion bancaire (US-21, US-22). Les écrans
 * (US-45, US-61) ne lisent que ce format : remplacer le bouchon ne les change pas.
 * Montant toujours positif ; le sens dit si l'argent entre ou sort du compte.
 */
public record Transaction(LocalDate date, String libelle, String tiers, BigDecimal montant, Sens sens,
		Source source) {

	public enum Sens {
		ENTREE,
		SORTIE
	}

	// Origine de la transaction : jamais de mélange entre données de démonstration et vraies données.
	public enum Source {
		BOUCHON,
		BANQUE
	}

}
