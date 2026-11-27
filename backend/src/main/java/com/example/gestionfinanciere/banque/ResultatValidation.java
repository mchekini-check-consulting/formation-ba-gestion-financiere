package com.example.gestionfinanciere.banque;

// US-30 : issue de la validation d'une opération ; message = refus à afficher ; transaction = ligne à jour.
public record ResultatValidation(Issue issue, String message, VueTransaction transaction) {

	public enum Issue {
		VALIDEE,
		// Dossier ou opération introuvable (mêmes droits que le dossier).
		INTROUVABLE,
		// Catégorie, compte ou taux refusé, compte absent ou désactivé du plan, écriture déséquilibrée.
		INVALIDE,
		DEJA_VALIDEE
	}

	static ResultatValidation refuse(Issue issue, String message) {
		return new ResultatValidation(issue, message, null);
	}

}
