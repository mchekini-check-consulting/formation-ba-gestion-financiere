package com.example.gestionfinanciere.equipe;

import java.util.Map;

// Issue d'une invitation (US-19).
public record ResultatInvitation(Statut statut, Map<String, String> erreurs, Membre membre) {

	public enum Statut {
		ENVOYEE,
		INVALIDE,
		EMAIL_DEJA_UTILISE
	}

	static ResultatInvitation envoyee(Membre membre) {
		return new ResultatInvitation(Statut.ENVOYEE, Map.of(), membre);
	}

	static ResultatInvitation invalide(Map<String, String> erreurs) {
		return new ResultatInvitation(Statut.INVALIDE, Map.copyOf(erreurs), null);
	}

	static ResultatInvitation emailDejaUtilise() {
		return new ResultatInvitation(Statut.EMAIL_DEJA_UTILISE, Map.of(), null);
	}

}
