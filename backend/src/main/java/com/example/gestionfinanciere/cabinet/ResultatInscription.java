package com.example.gestionfinanciere.cabinet;

import java.util.Map;

import com.example.gestionfinanciere.compte.DefiSms;

// Issue de l'étape 1 « Vos informations » (US-36) : code SMS envoyé, ou ce qui bloque.
public record ResultatInscription(Statut statut, Map<String, String> erreurs, DefiSms defi) {

	public enum Statut {
		CODE_ENVOYE,
		INVALIDE,
		SIREN_DEJA_UTILISE,
		EMAIL_DEJA_UTILISE
	}

	static ResultatInscription codeEnvoye(DefiSms defi) {
		return new ResultatInscription(Statut.CODE_ENVOYE, Map.of(), defi);
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
