package com.example.gestionfinanciere.facture;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * US-55 RG1 : les champs de la fiche facture, tels que le comptable les voit et les corrige (corps
 * de PUT .../informations et de POST .../validation). typeTiers : FOURNISSEUR ou CLIENT.
 */
public record InformationsFacture(String typeTiers, String nomTiers, String numeroFacture, LocalDate dateEmission,
		BigDecimal montantHt, BigDecimal montantTva, BigDecimal tauxTva, BigDecimal montantTtc,
		String compteContrepartie) {

	// RG8 : un champ changé, avec ses valeurs avant et après (vides si le champ était ou devient vide).
	public record Changement(ChampFacture champ, String ancienneValeur, String nouvelleValeur) {
	}

	static InformationsFacture de(Facture facture) {
		return new InformationsFacture(facture.getTypeTiers(), facture.getNomTiers(), facture.getNumeroFacture(),
				facture.getDateEmission(), facture.getMontantHt(), facture.getMontantTva(), facture.getTauxTva(),
				facture.getMontantTtc(), facture.getCompteContrepartie());
	}

	// Espaces retirés, texte vide = champ vide ; type de tiers et compte en majuscules (401MOULINS).
	InformationsFacture nettoyee() {
		return new InformationsFacture(majuscules(typeTiers), texte(nomTiers), texte(numeroFacture), dateEmission,
				montantHt, montantTva, tauxTva, montantTtc, majuscules(compteContrepartie));
	}

	// RG8 : champs dont la valeur change ; 186.4 et 186.40 sont le même montant.
	List<Changement> changementsVers(InformationsFacture nouvelles) {
		List<Changement> changements = new ArrayList<>();
		comparer(changements, ChampFacture.TYPE_TIERS, typeTiers, nouvelles.typeTiers);
		comparer(changements, ChampFacture.NOM_TIERS, nomTiers, nouvelles.nomTiers);
		comparer(changements, ChampFacture.NUMERO_FACTURE, numeroFacture, nouvelles.numeroFacture);
		comparer(changements, ChampFacture.DATE_EMISSION, dateEmission, nouvelles.dateEmission);
		comparer(changements, ChampFacture.MONTANT_HT, montantHt, nouvelles.montantHt);
		comparer(changements, ChampFacture.MONTANT_TVA, montantTva, nouvelles.montantTva);
		comparer(changements, ChampFacture.TAUX_TVA, tauxTva, nouvelles.tauxTva);
		comparer(changements, ChampFacture.MONTANT_TTC, montantTtc, nouvelles.montantTtc);
		comparer(changements, ChampFacture.COMPTE_CONTREPARTIE, compteContrepartie, nouvelles.compteContrepartie);
		return changements;
	}

	private static void comparer(List<Changement> changements, ChampFacture champ, Object avant, Object apres) {
		String ancienne = valeur(avant);
		String nouvelle = valeur(apres);
		if (!Objects.equals(ancienne, nouvelle)) {
			changements.add(new Changement(champ, ancienne, nouvelle));
		}
	}

	private static String valeur(Object valeur) {
		if (valeur instanceof BigDecimal montant) {
			return montant.stripTrailingZeros().toPlainString();
		}
		return valeur == null ? null : valeur.toString();
	}

	private static String texte(String valeur) {
		if (valeur == null || valeur.isBlank()) {
			return null;
		}
		return valeur.strip();
	}

	private static String majuscules(String valeur) {
		String nettoyee = texte(valeur);
		return nettoyee == null ? null : nettoyee.toUpperCase(Locale.ROOT);
	}

}
