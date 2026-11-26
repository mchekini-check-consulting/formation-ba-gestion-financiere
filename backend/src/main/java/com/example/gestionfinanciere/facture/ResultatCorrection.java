package com.example.gestionfinanciere.facture;

// US-55 : issue d'un enregistrement ou d'une validation ; message = refus à afficher ; facture = fiche à jour.
public record ResultatCorrection(Issue issue, String message, VueFacture facture) {

	public enum Issue {
		ENREGISTREE,
		// RG6 : facture passée en « Traitée ».
		VALIDEE,
		// US-56 RG5 : écriture recalculée.
		REGENEREE,
		INTROUVABLE,
		// Valeur refusée (format, compte absent du plan RG3), champ obligatoire manquant (RG5) ou
		// montants incohérents (RG4).
		INVALIDE,
		// Lecture en cours, facture de vente traitée ou écriture exportée (US-56 CA3) : ne se modifie pas.
		// US-56 : écriture d'une facture non validée ou de vente.
		FIGEE
	}

	static ResultatCorrection refuse(Issue issue, String message) {
		return new ResultatCorrection(issue, message, null);
	}

}
