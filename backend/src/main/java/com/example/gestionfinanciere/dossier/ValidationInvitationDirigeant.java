package com.example.gestionfinanciere.dossier;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.gestionfinanciere.cabinet.ValidationInscription;

// US-14 RG1 : prénom, nom et email du dirigeant. Mêmes messages que l'écran
// (frontend/src/app/dossiers/invitation-dirigeant.ts).
public final class ValidationInvitationDirigeant {

	// CA erreur.
	public static final String EMAIL_INVALIDE = "Saisissez une adresse email valide";

	private ValidationInvitationDirigeant() {
	}

	// Erreurs par champ (nom du champ -> message) ; vide si tout est correct.
	public static Map<String, String> erreurs(DemandeInvitationDirigeant demande) {
		Map<String, String> erreurs = new LinkedHashMap<>();
		texte(erreurs, "prenom", demande.prenom());
		texte(erreurs, "nom", demande.nom());
		if (ValidationInscription.estVide(demande.email())
				|| !ValidationInscription.emailValide(ValidationInscription.email(demande.email()))) {
			erreurs.put("email", EMAIL_INVALIDE);
		}
		return erreurs;
	}

	private static void texte(Map<String, String> erreurs, String champ, String valeur) {
		if (ValidationInscription.estVide(valeur)) {
			erreurs.put(champ, ValidationInscription.OBLIGATOIRE);
		} else if (valeur.trim().length() > 100) {
			erreurs.put(champ, ValidationInscription.TROP_LONG);
		}
	}

}
