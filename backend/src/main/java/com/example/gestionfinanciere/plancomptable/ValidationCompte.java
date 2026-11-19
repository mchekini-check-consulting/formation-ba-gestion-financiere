package com.example.gestionfinanciere.plancomptable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * US-41 Règles du formulaire « Nouveau compte ». Un numéro de compte commence par la classe (1 à
 * 7), puis au moins deux chiffres ; un sous-compte prolonge le numéro d'un compte du plan général
 * avec des chiffres ou des lettres majuscules (RG1, ex. 401MOULINS). CA2 : 9001 -> invalide.
 */
public final class ValidationCompte {

	public static final String NUMERO_OBLIGATOIRE = "Le numéro de compte est obligatoire";
	public static final String NUMERO_INVALIDE = "Numéro de compte invalide";
	public static final String NUMERO_SANS_COMPTE_PARENT =
			"Le numéro doit commencer par celui d'un compte du plan général (ex. 401MOULINS)";
	public static final String LIBELLE_OBLIGATOIRE = "Le libellé est obligatoire";
	public static final String LIBELLE_TROP_LONG = "Le libellé ne doit pas dépasser 200 caractères";
	static final int LIBELLE_MAX = 200;

	private static final Pattern NUMERO = Pattern.compile("^[1-7][0-9]{2}[0-9A-Z]{0,17}$");
	private static final Pattern ESPACES = Pattern.compile("\\s");

	private ValidationCompte() {
	}

	// Numéro tel qu'il est enregistré : sans espaces, en majuscules (« 401 moulins » -> 401MOULINS).
	public static String numero(String saisi) {
		return saisi == null ? "" : ESPACES.matcher(saisi).replaceAll("").toUpperCase(Locale.ROOT);
	}

	public static String libelle(String saisi) {
		return saisi == null ? "" : saisi.trim();
	}

	// Champs à corriger (nom du champ -> message) ; vide si tout est bon.
	public static Map<String, String> erreurs(DemandeCompte demande, Collection<String> comptesGeneraux) {
		Map<String, String> erreurs = new LinkedHashMap<>();
		String numero = numero(demande.numero());
		if (numero.isEmpty()) {
			erreurs.put("numero", NUMERO_OBLIGATOIRE);
		} else if (!NUMERO.matcher(numero).matches()) {
			erreurs.put("numero", NUMERO_INVALIDE);
		} else if (comptesGeneraux.stream()
				.noneMatch(general -> numero.length() > general.length() && numero.startsWith(general))) {
			erreurs.put("numero", NUMERO_SANS_COMPTE_PARENT);
		}
		String libelle = libelle(demande.libelle());
		if (libelle.isEmpty()) {
			erreurs.put("libelle", LIBELLE_OBLIGATOIRE);
		} else if (libelle.length() > LIBELLE_MAX) {
			erreurs.put("libelle", LIBELLE_TROP_LONG);
		}
		return erreurs;
	}

}
