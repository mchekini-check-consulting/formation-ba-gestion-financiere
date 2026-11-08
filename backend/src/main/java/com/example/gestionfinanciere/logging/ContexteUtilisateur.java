package com.example.gestionfinanciere.logging;

import org.slf4j.MDC;

/**
 * Personne connectée, écrite sur chaque ligne de log de la requête : numéros internes
 * uniquement ({@code utilisateur}, {@code cabinet}), jamais de nom ni d'email.
 * Posé dès que la session est reconnue ; effacé en fin de requête par {@link ContexteRequeteFilter}.
 */
public final class ContexteUtilisateur {

	static final String UTILISATEUR = "utilisateur";
	static final String CABINET = "cabinet";

	private ContexteUtilisateur() {
	}

	public static void definir(Long utilisateur, Long cabinet) {
		poser(UTILISATEUR, utilisateur);
		poser(CABINET, cabinet);
	}

	static void effacer() {
		MDC.remove(UTILISATEUR);
		MDC.remove(CABINET);
	}

	private static void poser(String cle, Long valeur) {
		if (valeur == null) {
			MDC.remove(cle);
		} else {
			MDC.put(cle, valeur.toString());
		}
	}

}
