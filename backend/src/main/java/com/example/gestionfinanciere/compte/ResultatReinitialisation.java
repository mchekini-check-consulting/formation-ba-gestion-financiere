package com.example.gestionfinanciere.compte;

import java.util.List;

// Issue d'un changement de mot de passe par lien (US-06).
public record ResultatReinitialisation(Statut statut, List<String> reglesNonRespectees) {

	public enum Statut {
		CHANGE,
		LIEN_EXPIRE,
		TROP_FAIBLE
	}

	static ResultatReinitialisation change() {
		return new ResultatReinitialisation(Statut.CHANGE, List.of());
	}

	static ResultatReinitialisation lienExpire() {
		return new ResultatReinitialisation(Statut.LIEN_EXPIRE, List.of());
	}

	static ResultatReinitialisation tropFaible(List<String> regles) {
		return new ResultatReinitialisation(Statut.TROP_FAIBLE, List.copyOf(regles));
	}

}
