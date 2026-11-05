package com.example.gestionfinanciere.cabinet;

// Formulaire « Créer un compte » vérifié et remis en forme : SIREN sur 9 chiffres sans espaces,
// email en minuscules, mobile au format 0612345678, code de parrainage absent = null.
public record FormulaireValide(String nomCabinet, String siren, String prenom, String nom, String email,
		String telephone, String motDePasse, int nombreDossiers, String codeParrainage) {
}
