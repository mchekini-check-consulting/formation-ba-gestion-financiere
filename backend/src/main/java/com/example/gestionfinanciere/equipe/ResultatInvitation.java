package com.example.gestionfinanciere.equipe;

import java.util.Map;

// Issue d'une invitation (US-19). jetonDeTest : lien d'invitation, en mode test uniquement.
public record ResultatInvitation(Statut statut, Map<String, String> erreurs, Membre membre, String jetonDeTest) {

	public enum Statut {
		ENVOYEE,
		INVALIDE,
		EMAIL_DEJA_UTILISE
	}

	static ResultatInvitation envoyee(Membre membre, String jetonDeTest) {
		return new ResultatInvitation(Statut.ENVOYEE, Map.of(), membre, jetonDeTest);
	}

	static ResultatInvitation invalide(Map<String, String> erreurs) {
		return new ResultatInvitation(Statut.INVALIDE, Map.copyOf(erreurs), null, null);
	}

	static ResultatInvitation emailDejaUtilise() {
		return new ResultatInvitation(Statut.EMAIL_DEJA_UTILISE, Map.of(), null, null);
	}

}
