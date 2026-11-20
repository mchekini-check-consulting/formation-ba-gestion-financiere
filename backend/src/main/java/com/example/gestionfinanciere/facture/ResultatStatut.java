package com.example.gestionfinanciere.facture;

// US-51 : issue d'un changement de statut ; facture = la ligne à jour quand le statut a changé.
public record ResultatStatut(Issue issue, LigneFacture facture) {

	public enum Issue {
		CHANGE,
		INTROUVABLE,
		// RG2 / CA4 : passage non autorisé depuis le statut actuel.
		PASSAGE_INTERDIT,
		// RG3 / CA3 : « À compléter » sans motif.
		MOTIF_MANQUANT,
		MOTIF_TROP_LONG
	}

	static ResultatStatut refuse(Issue issue) {
		return new ResultatStatut(issue, null);
	}

}
