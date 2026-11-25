package com.example.gestionfinanciere.facture;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

/**
 * US-55 RG1 et RG2 : informations d'une facture saisies ou corrigées sur la fiche. Un champ vide
 * (null) n'est pas renseigné. typeTiers : FOURNISSEUR ou CLIENT ; compteContrepartie : numéro d'un
 * compte du plan comptable du dossier (RG3). Taux vide d'une facture à plusieurs taux : « Multiple ».
 */
public record InformationsFacture(String typeTiers, String nomTiers, String numeroFacture, LocalDate dateEmission,
		BigDecimal montantHt, BigDecimal montantTva, BigDecimal tauxTva, BigDecimal montantTtc,
		String compteContrepartie) {

	// Textes sans espaces autour ; texte vide = non renseigné.
	InformationsFacture nettoyees() {
		return new InformationsFacture(texte(typeTiers), texte(nomTiers), texte(numeroFacture), dateEmission,
				montantHt, montantTva, tauxTva, montantTtc, majuscules(texte(compteContrepartie)));
	}

	// Numéro de compte comme dans le plan comptable (401MOULINS).
	private static String majuscules(String valeur) {
		return valeur == null ? null : valeur.toUpperCase(Locale.ROOT);
	}

	private static String texte(String valeur) {
		if (valeur == null || valeur.isBlank()) {
			return null;
		}
		return valeur.strip();
	}

}
