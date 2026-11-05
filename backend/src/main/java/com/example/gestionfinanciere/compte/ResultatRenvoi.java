package com.example.gestionfinanciere.compte;

// Issue d'une demande « Renvoyer un code » (US-04 RG2).
public record ResultatRenvoi(Statut statut, long secondesRestantes, String telephoneMasque, String codeDeTest) {

	public enum Statut {
		RENVOYE,
		TROP_TOT,
		DEMANDE_INVALIDE
	}

	public static ResultatRenvoi renvoye(String telephoneMasque, String codeDeTest) {
		return new ResultatRenvoi(Statut.RENVOYE, 0, telephoneMasque, codeDeTest);
	}

	public static ResultatRenvoi tropTot(long secondesRestantes) {
		return new ResultatRenvoi(Statut.TROP_TOT, secondesRestantes, null, null);
	}

	public static ResultatRenvoi demandeInvalide() {
		return new ResultatRenvoi(Statut.DEMANDE_INVALIDE, 0, null, null);
	}

}
