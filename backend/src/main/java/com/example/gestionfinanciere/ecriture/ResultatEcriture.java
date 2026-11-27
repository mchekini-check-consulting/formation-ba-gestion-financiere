package com.example.gestionfinanciere.ecriture;

/**
 * US-47 Issue d'une correction d'écriture : écriture corrigée (MODIFIEE), ou refus avec son message
 * (INVALIDE : formulaire ou RG3 ; EXPORTEE : RG2), ou introuvable.
 */
public record ResultatEcriture(Statut statut, JournalEcritures.Ligne ecriture, String message) {

	public enum Statut {
		MODIFIEE, INVALIDE, EXPORTEE, ECRITURE_INTROUVABLE, DOSSIER_INTROUVABLE
	}

	static ResultatEcriture modifiee(JournalEcritures.Ligne ecriture) {
		return new ResultatEcriture(Statut.MODIFIEE, ecriture, null);
	}

	static ResultatEcriture refus(Statut statut, String message) {
		return new ResultatEcriture(statut, null, message);
	}

	static ResultatEcriture de(Statut statut) {
		return new ResultatEcriture(statut, null, null);
	}

}
