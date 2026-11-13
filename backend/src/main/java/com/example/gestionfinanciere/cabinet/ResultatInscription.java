package com.example.gestionfinanciere.cabinet;

import java.util.Map;

import com.example.gestionfinanciere.compte.Compte;

// Issue du formulaire « Vos informations » (US-36) : cabinet créé avec son administrateur, ou ce qui bloque.
public record ResultatInscription(Statut statut, Map<String, String> erreurs, Compte compte) {

	public enum Statut {
		CABINET_CREE,
		INVALIDE,
		SIREN_DEJA_UTILISE,
		EMAIL_DEJA_UTILISE
	}

	static ResultatInscription cabinetCree(Compte administrateur) {
		return new ResultatInscription(Statut.CABINET_CREE, Map.of(), administrateur);
	}

	static ResultatInscription invalide(Map<String, String> erreurs) {
		return new ResultatInscription(Statut.INVALIDE, Map.copyOf(erreurs), null);
	}

	static ResultatInscription sirenDejaUtilise() {
		return new ResultatInscription(Statut.SIREN_DEJA_UTILISE, Map.of(), null);
	}

	static ResultatInscription emailDejaUtilise() {
		return new ResultatInscription(Statut.EMAIL_DEJA_UTILISE, Map.of(), null);
	}

}
