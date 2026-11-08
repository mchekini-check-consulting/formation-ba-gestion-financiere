package com.example.gestionfinanciere.dossier;

// US-09 RG1 / US-14 RG3 : état de l'espace client d'un dossier, déduit du compte du dirigeant.
public enum EtatEspaceClient {
	NON_INVITE,
	INVITE,
	ACTIF;

	// Aucun compte dirigeant -> Non invité ; compte encore inactif -> Invité ; activé -> Actif.
	static EtatEspaceClient de(long comptesDirigeant, long comptesActifs) {
		if (comptesActifs > 0) {
			return ACTIF;
		}
		return comptesDirigeant > 0 ? INVITE : NON_INVITE;
	}
}
