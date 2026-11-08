package com.example.gestionfinanciere.dossier;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.gestionfinanciere.cabinet.ValidationInscription;

// US-10 RG1 : champs du formulaire « Créer un dossier ». Mêmes messages que l'écran
// (frontend/src/app/dossiers/creer-dossier.ts) et que le formulaire « Créer un compte » (US-36).
public final class ValidationDossier {

	static final int RAISON_SOCIALE_MAX = 200;

	private ValidationDossier() {
	}

	// Erreurs par champ (nom du champ -> message) ; vide si tout est correct.
	public static Map<String, String> erreurs(DemandeDossier demande) {
		Map<String, String> erreurs = new LinkedHashMap<>();
		if (ValidationInscription.estVide(demande.raisonSociale())) {
			erreurs.put("raisonSociale", ValidationInscription.OBLIGATOIRE);
		} else if (demande.raisonSociale().trim().length() > RAISON_SOCIALE_MAX) {
			erreurs.put("raisonSociale", ValidationInscription.TROP_LONG);
		}
		if (ValidationInscription.estVide(demande.siren())) {
			erreurs.put("siren", ValidationInscription.OBLIGATOIRE);
		} else if (!ValidationInscription.sirenValide(ValidationInscription.siren(demande.siren()))) {
			erreurs.put("siren", ValidationInscription.SIREN_INVALIDE);
		}
		return erreurs;
	}

}
