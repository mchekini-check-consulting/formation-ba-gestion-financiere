package com.example.gestionfinanciere.compte;

// Réponse d'un email + mot de passe corrects : l'écran passe au code reçu par email (US-04).
// codeDeTest n'est renseigné qu'en mode test (aucune API d'envoi d'emails branchée).
public record ConnexionResponse(String etapeSuivante, String defi, String emailMasque, String codeDeTest) {

	public static final String ETAPE_CODE_EMAIL = "CODE_EMAIL";

	public static ConnexionResponse versCodeEmail(DefiCode defi) {
		return new ConnexionResponse(ETAPE_CODE_EMAIL, defi.defi(), defi.emailMasque(), defi.codeDeTest());
	}

}
