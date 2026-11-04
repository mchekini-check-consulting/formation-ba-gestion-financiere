package com.example.gestionfinanciere.compte;

// Réponse d'un email + mot de passe corrects : l'écran passe au code SMS (US-04).
// codeDeTest n'est renseigné qu'en mode test (aucun opérateur SMS branché).
public record ConnexionResponse(String etapeSuivante, String defi, String telephoneMasque, String codeDeTest) {

	public static final String ETAPE_CODE_SMS = "CODE_SMS";

	public static ConnexionResponse versCodeSms(DefiSms defi) {
		return new ConnexionResponse(ETAPE_CODE_SMS, defi.defi(), defi.telephoneMasque(), defi.codeDeTest());
	}

}
