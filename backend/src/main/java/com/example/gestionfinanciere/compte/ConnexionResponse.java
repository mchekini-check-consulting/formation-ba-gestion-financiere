package com.example.gestionfinanciere.compte;

// Réponse d'une connexion réussie : l'écran passe ensuite au code SMS (US-04).
public record ConnexionResponse(Role role, String etapeSuivante) {

	public static final String ETAPE_CODE_SMS = "CODE_SMS";

	public static ConnexionResponse versCodeSms(Role role) {
		return new ConnexionResponse(role, ETAPE_CODE_SMS);
	}

}
