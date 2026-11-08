package com.example.gestionfinanciere.dossier;

import java.util.Map;

// Issue de la création d'un dossier (US-10).
public record ResultatCreationDossier(Statut statut, Map<String, String> erreurs, VueDossier dossier) {

	public enum Statut {
		CREE,
		INVALIDE,
		SIREN_DEJA_UTILISE
	}

	static ResultatCreationDossier cree(VueDossier dossier) {
		return new ResultatCreationDossier(Statut.CREE, Map.of(), dossier);
	}

	static ResultatCreationDossier invalide(Map<String, String> erreurs) {
		return new ResultatCreationDossier(Statut.INVALIDE, Map.copyOf(erreurs), null);
	}

	static ResultatCreationDossier sirenDejaUtilise() {
		return new ResultatCreationDossier(Statut.SIREN_DEJA_UTILISE, Map.of(), null);
	}

}
