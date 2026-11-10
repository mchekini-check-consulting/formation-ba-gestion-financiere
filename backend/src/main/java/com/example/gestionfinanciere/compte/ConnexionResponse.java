package com.example.gestionfinanciere.compte;

// Réponse d'un email + mot de passe corrects : l'écran passe au code reçu par email (US-04).
public record ConnexionResponse(String etapeSuivante, String defi, String emailMasque) {

	public static final String ETAPE_CODE_EMAIL = "CODE_EMAIL";

	public static ConnexionResponse versCodeEmail(DefiCode defi) {
		return new ConnexionResponse(ETAPE_CODE_EMAIL, defi.defi(), defi.emailMasque());
	}

}
