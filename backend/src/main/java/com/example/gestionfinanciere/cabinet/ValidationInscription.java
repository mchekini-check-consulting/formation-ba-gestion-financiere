package com.example.gestionfinanciere.cabinet;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.example.gestionfinanciere.compte.RegleMotDePasse;

// US-36 RG2 : champs du formulaire « Créer un compte ». Mêmes messages que l'écran
// (frontend/src/app/creer-compte/validation.ts), qui fait les mêmes vérifications avant l'envoi.
public final class ValidationInscription {

	public static final String OBLIGATOIRE = "Ce champ est obligatoire";
	public static final String TROP_LONG = "Ce champ est trop long";
	public static final String SIREN_INVALIDE = "Le SIREN comporte 9 chiffres";
	public static final String EMAIL_INVALIDE = "Adresse email invalide";
	public static final String MOT_DE_PASSE_FAIBLE = "Le mot de passe ne respecte pas toutes les règles";
	public static final String DOSSIERS_INVALIDE = "Indiquez au moins 1 dossier";
	public static final String PARRAINAGE_INVALIDE = "Code de parrainage invalide";
	public static final String CGU_NON_ACCEPTEES = "Vous devez accepter les CGU pour créer votre compte";

	static final int DOSSIERS_MAX = 100_000;
	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final Pattern SIREN = Pattern.compile("^\\d{9}$");
	private static final Pattern SEPARATEURS = Pattern.compile("[\\s.\\-]");

	private ValidationInscription() {
	}

	// Erreurs par champ (nom du champ du formulaire -> message) ; vide si tout est correct.
	public static Map<String, String> erreurs(DemandeInscription demande) {
		Map<String, String> erreurs = new LinkedHashMap<>();
		texte(erreurs, "nomCabinet", demande.nomCabinet(), 200);
		if (estVide(demande.siren())) {
			erreurs.put("siren", OBLIGATOIRE);
		} else if (!SIREN.matcher(siren(demande.siren())).matches()) {
			erreurs.put("siren", SIREN_INVALIDE);
		}
		texte(erreurs, "prenom", demande.prenom(), 100);
		texte(erreurs, "nom", demande.nom(), 100);
		if (estVide(demande.email())) {
			erreurs.put("email", OBLIGATOIRE);
		} else if (!emailValide(email(demande.email()))) {
			erreurs.put("email", EMAIL_INVALIDE);
		}
		if (estVide(demande.motDePasse())) {
			erreurs.put("motDePasse", OBLIGATOIRE);
		} else if (!RegleMotDePasse.nonRespectees(demande.motDePasse()).isEmpty()) {
			erreurs.put("motDePasse", MOT_DE_PASSE_FAIBLE);
		}
		if (demande.nombreDossiers() == null) {
			erreurs.put("nombreDossiers", OBLIGATOIRE);
		} else if (demande.nombreDossiers() < 1 || demande.nombreDossiers() > DOSSIERS_MAX) {
			erreurs.put("nombreDossiers", DOSSIERS_INVALIDE);
		}
		if (!estVide(demande.codeParrainage()) && demande.codeParrainage().trim().length() > 20) {
			erreurs.put("codeParrainage", PARRAINAGE_INVALIDE);
		}
		if (!Boolean.TRUE.equals(demande.cguAcceptees())) {
			erreurs.put("cguAcceptees", CGU_NON_ACCEPTEES);
		}
		return erreurs;
	}

	// À appeler seulement si erreurs(demande) est vide.
	public static FormulaireValide remettreEnForme(DemandeInscription demande) {
		String parrainage = estVide(demande.codeParrainage()) ? null
				: demande.codeParrainage().trim().toUpperCase(Locale.ROOT);
		return new FormulaireValide(demande.nomCabinet().trim(), siren(demande.siren()), demande.prenom().trim(),
				demande.nom().trim(), email(demande.email()), demande.motDePasse(),
				demande.nombreDossiers(), parrainage);
	}

	// « 123 456 789 » -> « 123456789 ».
	static String siren(String saisie) {
		return SEPARATEURS.matcher(saisie).replaceAll("");
	}

	// US-03 RG1 : l'email est l'identifiant, les majuscules ne comptent pas.
	public static String email(String saisie) {
		return saisie.trim().toLowerCase(Locale.ROOT);
	}

	private static void texte(Map<String, String> erreurs, String champ, String valeur, int longueurMax) {
		if (estVide(valeur)) {
			erreurs.put(champ, OBLIGATOIRE);
		} else if (valeur.trim().length() > longueurMax) {
			erreurs.put(champ, TROP_LONG);
		}
	}

	// Format de l'email, déjà remis en forme par email(...).
	public static boolean emailValide(String email) {
		return email.length() <= 254 && EMAIL.matcher(email).matches();
	}

	public static boolean estVide(String valeur) {
		return valeur == null || valeur.isBlank();
	}

}
