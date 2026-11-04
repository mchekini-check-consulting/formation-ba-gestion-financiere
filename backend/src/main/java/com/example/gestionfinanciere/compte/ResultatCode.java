package com.example.gestionfinanciere.compte;

// Issue de la vérification d'un code SMS (US-04).
public record ResultatCode(Statut statut, int tentativesRestantes, Compte compte) {

	public enum Statut {
		REUSSI,
		FAUX,
		TROP_DE_CODES_FAUX,
		EXPIRE,
		DEMANDE_INVALIDE
	}

	static ResultatCode reussi(Compte compte) {
		return new ResultatCode(Statut.REUSSI, 0, compte);
	}

	static ResultatCode faux(int tentativesRestantes) {
		return new ResultatCode(Statut.FAUX, tentativesRestantes, null);
	}

	static ResultatCode tropDeCodesFaux() {
		return new ResultatCode(Statut.TROP_DE_CODES_FAUX, 0, null);
	}

	static ResultatCode expire(int tentativesRestantes) {
		return new ResultatCode(Statut.EXPIRE, tentativesRestantes, null);
	}

	static ResultatCode demandeInvalide() {
		return new ResultatCode(Statut.DEMANDE_INVALIDE, 0, null);
	}

}
