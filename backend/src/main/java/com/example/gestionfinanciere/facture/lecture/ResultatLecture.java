package com.example.gestionfinanciere.facture.lecture;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.gestionfinanciere.facture.ChampFacture;

/**
 * US-53 : ce que la lecture a trouvé. lisible faux : « Lecture échouée » (CA4). confiances : niveau
 * de confiance (0 à 1) de chaque champ lu ; aVerifier : champs lus avec un doute (RG4, RG5). Un champ
 * absent (valeur vide) est « À renseigner » (RG3). US-54 CA2 : detailTva, la TVA par taux quand la
 * facture en porte plusieurs (tauxTva reste vide, « Multiple »).
 */
public record ResultatLecture(boolean lisible, String typeTiers, String nomTiers, String numeroFacture,
		LocalDate dateEmission, BigDecimal montantHt, BigDecimal montantTva, BigDecimal tauxTva,
		BigDecimal montantTtc, String compteContrepartie, Map<ChampFacture, Double> confiances,
		Set<ChampFacture> aVerifier, List<TvaParTaux> detailTva) {

	public ResultatLecture {
		confiances = confiances.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(confiances));
		aVerifier = aVerifier.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(aVerifier));
		detailTva = List.copyOf(detailTva);
	}

	// CA4 : rien d'exploitable dans le fichier.
	public static ResultatLecture illisible() {
		return new ResultatLecture(false, null, null, null, null, null, null, null, null, null, Map.of(), Set.of(),
				List.of());
	}

	// US-54 CA2 : plusieurs taux de TVA sur la facture.
	public boolean tauxMultiple() {
		return detailTva.size() > 1;
	}

	// RG3 : champs non lus, laissés vides.
	public Set<ChampFacture> aRenseigner() {
		Set<ChampFacture> vides = EnumSet.noneOf(ChampFacture.class);
		if (!lisible) {
			return vides;
		}
		ajouterSiVide(vides, ChampFacture.TYPE_TIERS, typeTiers);
		ajouterSiVide(vides, ChampFacture.NOM_TIERS, nomTiers);
		ajouterSiVide(vides, ChampFacture.NUMERO_FACTURE, numeroFacture);
		ajouterSiVide(vides, ChampFacture.DATE_EMISSION, dateEmission);
		ajouterSiVide(vides, ChampFacture.MONTANT_HT, montantHt);
		ajouterSiVide(vides, ChampFacture.MONTANT_TVA, montantTva);
		if (!tauxMultiple()) {
			ajouterSiVide(vides, ChampFacture.TAUX_TVA, tauxTva);
		}
		ajouterSiVide(vides, ChampFacture.MONTANT_TTC, montantTtc);
		ajouterSiVide(vides, ChampFacture.COMPTE_CONTREPARTIE, compteContrepartie);
		return vides;
	}

	// Pastille « À vérifier » de la liste : au moins un champ à vérifier ou à renseigner.
	public boolean aVerifierOuARenseigner() {
		return !aVerifier.isEmpty() || !aRenseigner().isEmpty();
	}

	private static void ajouterSiVide(Set<ChampFacture> vides, ChampFacture champ, Object valeur) {
		if (valeur == null) {
			vides.add(champ);
		}
	}

}
