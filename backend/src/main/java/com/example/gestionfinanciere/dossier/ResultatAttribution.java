package com.example.gestionfinanciere.dossier;

// Issue d'une attribution (US-20) : comptables enregistrés, ou refus.
public record ResultatAttribution(Statut statut, ComptablesDossier comptables) {

	public enum Statut {
		ATTRIBUE,
		// RG1 : un comptable n'attribue pas les dossiers.
		INTERDIT,
		// Dossier inexistant, d'un autre cabinet ou non attribué (US-11 RG3, RG2).
		INTROUVABLE,
		// Compte qui n'est pas un comptable du cabinet.
		INVALIDE
	}

	static ResultatAttribution attribue(ComptablesDossier comptables) {
		return new ResultatAttribution(Statut.ATTRIBUE, comptables);
	}

	static ResultatAttribution refus(Statut statut) {
		return new ResultatAttribution(statut, null);
	}

}
