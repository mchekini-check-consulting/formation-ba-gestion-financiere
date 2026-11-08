package com.example.gestionfinanciere.compte;

// Issue d'une demande « Renvoyer un code » (US-04 RG2, US-36).
public record ResultatRenvoi(Statut statut, long secondesRestantes, String codeDeTest) {

	public enum Statut {
		RENVOYE,
		TROP_TOT,
		DEMANDE_INVALIDE
	}

	public static ResultatRenvoi renvoye(String codeDeTest) {
		return new ResultatRenvoi(Statut.RENVOYE, 0, codeDeTest);
	}

	public static ResultatRenvoi tropTot(long secondesRestantes) {
		return new ResultatRenvoi(Statut.TROP_TOT, secondesRestantes, null);
	}

	public static ResultatRenvoi demandeInvalide() {
		return new ResultatRenvoi(Statut.DEMANDE_INVALIDE, 0, null);
	}

}
