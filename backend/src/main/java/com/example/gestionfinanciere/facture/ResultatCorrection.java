package com.example.gestionfinanciere.facture;

// US-55 : issue d'un enregistrement ou d'une validation ; message = refus à afficher ; facture = fiche à jour.
public record ResultatCorrection(Issue issue, String message, VueFacture facture) {

	public enum Issue {
		ENREGISTREE,
		// RG6 : facture passée en « Traitée ».
		VALIDEE,
		INTROUVABLE,
		// Valeur refusée (format, compte absent du plan RG3), champ obligatoire manquant (RG5) ou
		// montants incohérents (RG4).
		INVALIDE,
		// Facture déjà traitée ou lecture en cours : ses informations ne se modifient pas.
		FIGEE
	}

	static ResultatCorrection refuse(Issue issue, String message) {
		return new ResultatCorrection(issue, message, null);
	}

}
