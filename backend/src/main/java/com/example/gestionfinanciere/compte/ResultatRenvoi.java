package com.example.gestionfinanciere.compte;

// Issue d'une demande « Renvoyer un code » (US-04 RG2, US-36).
public record ResultatRenvoi(Statut statut, long secondesRestantes) {

	public enum Statut {
		RENVOYE,
		TROP_TOT,
		DEMANDE_INVALIDE
	}

	public static ResultatRenvoi renvoye() {
		return new ResultatRenvoi(Statut.RENVOYE, 0);
	}

	public static ResultatRenvoi tropTot(long secondesRestantes) {
		return new ResultatRenvoi(Statut.TROP_TOT, secondesRestantes);
	}

	public static ResultatRenvoi demandeInvalide() {
		return new ResultatRenvoi(Statut.DEMANDE_INVALIDE, 0);
	}

}
