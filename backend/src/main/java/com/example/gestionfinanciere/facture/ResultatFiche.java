package com.example.gestionfinanciere.facture;

// US-55 : issue d'un enregistrement ou d'une validation de la fiche facture. facture : la fiche à
// jour (enregistrée ou validée) ; message : la raison d'un refus (RG3, RG4, RG5) ; suivante : la
// prochaine facture à traiter du même onglet (RG6 « Valider et passer à la suivante »), ou vide.
public record ResultatFiche(Issue issue, String message, VueFacture facture, Long suivante) {

	public enum Issue {
		ENREGISTREE,
		VALIDEE,
		INTROUVABLE,
		// RG8 : facture traitée (ou en cours de lecture), en lecture seule.
		NON_MODIFIABLE,
		// RG3, RG4, RG5 ou valeur impossible : rien n'est enregistré, le statut ne change pas.
		REFUSEE
	}

	static ResultatFiche refuse(Issue issue) {
		return new ResultatFiche(issue, null, null, null);
	}

	static ResultatFiche refuse(String message) {
		return new ResultatFiche(Issue.REFUSEE, message, null, null);
	}

}
