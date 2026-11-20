package com.example.gestionfinanciere.plancomptable;

import java.util.Map;

// US-41 Résultat d'une création, activation ou suppression de compte.
public record ResultatCompte(Statut statut, VueCompte compte, Map<String, String> erreurs) {

	public enum Statut {
		CREE,
		MODIFIE,
		SUPPRIME,
		INVALIDE,
		DEJA_EXISTANT,
		COMPTE_INTROUVABLE,
		DOSSIER_INTROUVABLE,
		COMPTE_GENERAL
	}

	static ResultatCompte de(Statut statut) {
		return new ResultatCompte(statut, null, Map.of());
	}

	static ResultatCompte de(Statut statut, VueCompte compte) {
		return new ResultatCompte(statut, compte, Map.of());
	}

	static ResultatCompte invalide(Map<String, String> erreurs) {
		return new ResultatCompte(Statut.INVALIDE, null, erreurs);
	}

}
