package com.example.gestionfinanciere.activation;

import java.util.List;
import java.util.Map;

import com.example.gestionfinanciere.compte.Compte;

/**
 * Issue de la vérification du lien ou de l'activation (US-08). compte : la personne invitée
 * (lien valable) ou activée ; erreurs et reglesNonRespectees : formulaire à corriger.
 */
public record ResultatActivation(Statut statut, Compte compte, Map<String, String> erreurs,
		List<String> reglesNonRespectees) {

	public enum Statut {
		VALABLE,
		ACTIVE,
		INVALIDE,
		// Lien expiré, annulé par un renvoi ou inconnu.
		EXPIREE,
		// Lien déjà utilisé ou compte déjà actif.
		DEJA_ACTIVE
	}

	static ResultatActivation de(Statut statut, Compte compte) {
		return new ResultatActivation(statut, compte, Map.of(), List.of());
	}

	static ResultatActivation refus(Statut statut) {
		return de(statut, null);
	}

	static ResultatActivation invalide(Map<String, String> erreurs, List<String> regles) {
		return new ResultatActivation(Statut.INVALIDE, null, Map.copyOf(erreurs), List.copyOf(regles));
	}

}
