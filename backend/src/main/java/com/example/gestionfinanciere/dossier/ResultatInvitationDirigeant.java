package com.example.gestionfinanciere.dossier;

import java.util.Map;

// Issue d'une invitation ou d'un renvoi (US-14). jetonDeTest : lien d'invitation, en mode test uniquement.
public record ResultatInvitationDirigeant(Statut statut, Map<String, String> erreurs, String jetonDeTest) {

	public enum Statut {
		ENVOYEE,
		INVALIDE,
		// Dossier inexistant ou d'un autre cabinet (US-11 RG3).
		INTROUVABLE,
		EMAIL_DEJA_UTILISE,
		// Inviter un dirigeant déjà invité ou actif ; renvoyer sans invitation en attente (RG3).
		ETAT_INCOMPATIBLE
	}

	static ResultatInvitationDirigeant envoyee(String jetonDeTest) {
		return new ResultatInvitationDirigeant(Statut.ENVOYEE, Map.of(), jetonDeTest);
	}

	static ResultatInvitationDirigeant invalide(Map<String, String> erreurs) {
		return new ResultatInvitationDirigeant(Statut.INVALIDE, Map.copyOf(erreurs), null);
	}

	static ResultatInvitationDirigeant refus(Statut statut) {
		return new ResultatInvitationDirigeant(statut, Map.of(), null);
	}

}
