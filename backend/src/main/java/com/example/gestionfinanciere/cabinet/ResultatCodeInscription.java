package com.example.gestionfinanciere.cabinet;

import com.example.gestionfinanciere.compte.Compte;

// Issue de l'étape 2 « Code email » (US-36) : cabinet créé avec son administrateur, ou refus.
public record ResultatCodeInscription(Statut statut, int tentativesRestantes, Compte compte) {

	public enum Statut {
		CABINET_CREE,
		FAUX,
		TROP_DE_CODES_FAUX,
		EXPIRE,
		DEMANDE_INVALIDE,
		// Un autre cabinet a pris ce SIREN (ou cet email) entre le formulaire et le code.
		SIREN_DEJA_UTILISE,
		EMAIL_DEJA_UTILISE
	}

	static ResultatCodeInscription cabinetCree(Compte administrateur) {
		return new ResultatCodeInscription(Statut.CABINET_CREE, 0, administrateur);
	}

	static ResultatCodeInscription faux(int tentativesRestantes) {
		return new ResultatCodeInscription(Statut.FAUX, tentativesRestantes, null);
	}

	static ResultatCodeInscription autre(Statut statut, int tentativesRestantes) {
		return new ResultatCodeInscription(statut, tentativesRestantes, null);
	}

}
