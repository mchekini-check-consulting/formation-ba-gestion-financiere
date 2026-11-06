package com.example.gestionfinanciere.equipe;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.gestionfinanciere.cabinet.ValidationInscription;
import com.example.gestionfinanciere.compte.RoleCabinet;

// US-19 : champs du formulaire « Inviter un collaborateur ». Mêmes messages que l'écran
// (frontend/src/app/equipe/equipe.ts) et que le formulaire « Créer un compte » (US-36).
public final class ValidationInvitation {

	public static final String ROLE_INVALIDE = "Choisissez un rôle";

	private ValidationInvitation() {
	}

	// Erreurs par champ (nom du champ -> message) ; vide si tout est correct.
	public static Map<String, String> erreurs(DemandeInvitation demande) {
		Map<String, String> erreurs = new LinkedHashMap<>();
		texte(erreurs, "prenom", demande.prenom());
		texte(erreurs, "nom", demande.nom());
		if (ValidationInscription.estVide(demande.email())) {
			erreurs.put("email", ValidationInscription.OBLIGATOIRE);
		} else if (!ValidationInscription.emailValide(ValidationInscription.email(demande.email()))) {
			erreurs.put("email", ValidationInscription.EMAIL_INVALIDE);
		}
		if (role(demande.role()) == null) {
			erreurs.put("role", ROLE_INVALIDE);
		}
		return erreurs;
	}

	// RG2 : un des trois rôles, sinon null.
	static RoleCabinet role(String saisie) {
		return Arrays.stream(RoleCabinet.values()).filter(r -> r.name().equals(saisie)).findFirst().orElse(null);
	}

	private static void texte(Map<String, String> erreurs, String champ, String valeur) {
		if (ValidationInscription.estVide(valeur)) {
			erreurs.put(champ, ValidationInscription.OBLIGATOIRE);
		} else if (valeur.trim().length() > 100) {
			erreurs.put(champ, ValidationInscription.TROP_LONG);
		}
	}

}
