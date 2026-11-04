package com.example.gestionfinanciere.compte;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// US-06 RG3 : 12 caractères minimum, une majuscule, une minuscule, un chiffre et un
// caractère spécial. Mêmes libellés que l'écran (frontend/src/app/mot-de-passe/regles.ts).
public final class RegleMotDePasse {

	public static final String LONGUEUR = "12 caractères minimum";
	public static final String MAJUSCULE = "Une majuscule";
	public static final String MINUSCULE = "Une minuscule";
	public static final String CHIFFRE = "Un chiffre";
	public static final String SPECIAL = "Un caractère spécial";
	// Limite technique de BCrypt (72 octets).
	public static final String LONGUEUR_MAX = "64 caractères maximum";
	private static final int OCTETS_MAX = 72;

	private RegleMotDePasse() {
	}

	public static List<String> nonRespectees(String motDePasse) {
		String valeur = motDePasse == null ? "" : motDePasse;
		List<String> regles = new ArrayList<>();
		if (valeur.length() < 12) {
			regles.add(LONGUEUR);
		}
		if (valeur.chars().noneMatch(Character::isUpperCase)) {
			regles.add(MAJUSCULE);
		}
		if (valeur.chars().noneMatch(Character::isLowerCase)) {
			regles.add(MINUSCULE);
		}
		if (valeur.chars().noneMatch(Character::isDigit)) {
			regles.add(CHIFFRE);
		}
		if (valeur.chars().allMatch(Character::isLetterOrDigit)) {
			regles.add(SPECIAL);
		}
		if (valeur.length() > 64 || valeur.getBytes(StandardCharsets.UTF_8).length > OCTETS_MAX) {
			regles.add(LONGUEUR_MAX);
		}
		return regles;
	}

}
