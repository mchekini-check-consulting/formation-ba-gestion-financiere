package com.example.gestionfinanciere.facture;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.example.gestionfinanciere.facture.lecture.AnalyseurFacture;
import com.example.gestionfinanciere.facture.lecture.TvaParTaux;

// US-13 : en-tête de la page d'aperçu (nom et type du fichier, onglet du dossier).
// US-53 : résultat de la lecture automatique ; vide pour une facture jamais lue.
public record VueFacture(Long id, String nomFichier, String typeFichier, Facture.Sens sens, Lecture lecture) {

	/**
	 * US-53 RG2 à RG6 : statut et champs lus. aVerifier : lus avec un doute (RG4, RG5) ; aRenseigner :
	 * non lus (RG3). Champs vides tant que la lecture est en cours ou si elle a échoué. US-54 CA2 :
	 * tauxMultiple (taux affiché « Multiple ») et TVA par taux, du plus haut au plus bas.
	 */
	public record Lecture(Facture.StatutLecture statut, String typeTiers, String nomTiers, String numeroFacture,
			LocalDate dateEmission, BigDecimal montantHt, BigDecimal montantTva, BigDecimal tauxTva,
			boolean tauxMultiple, List<TvaParTaux> detailTva, BigDecimal montantTtc, String compteContrepartie,
			String libelleContrepartie, List<ChampFacture> aVerifier, List<ChampFacture> aRenseigner) {
	}

	static VueFacture de(Facture facture, List<ChampLu> champsLus) {
		return new VueFacture(facture.getId(), facture.getNomFichier(), facture.getTypeFichier(), facture.getSens(),
				lecture(facture, champsLus));
	}

	private static Lecture lecture(Facture facture, List<ChampLu> champsLus) {
		Facture.StatutLecture statut = facture.getLectureStatut();
		if (statut == null) {
			return null;
		}
		if (statut != Facture.StatutLecture.LUE) {
			return new Lecture(statut, null, null, null, null, null, null, null, false, List.of(), null, null, null,
					List.of(), List.of());
		}
		List<ChampFacture> aVerifier = champsLus.stream().filter(ChampLu::isAVerifier).map(ChampLu::getChamp).sorted()
				.toList();
		Set<ChampFacture> aRenseigner = EnumSet.noneOf(ChampFacture.class);
		ajouterSiVide(aRenseigner, ChampFacture.TYPE_TIERS, facture.getTypeTiers());
		ajouterSiVide(aRenseigner, ChampFacture.NOM_TIERS, facture.getNomTiers());
		ajouterSiVide(aRenseigner, ChampFacture.NUMERO_FACTURE, facture.getNumeroFacture());
		ajouterSiVide(aRenseigner, ChampFacture.DATE_EMISSION, facture.getDateEmission());
		ajouterSiVide(aRenseigner, ChampFacture.MONTANT_HT, facture.getMontantHt());
		ajouterSiVide(aRenseigner, ChampFacture.MONTANT_TVA, facture.getMontantTva());
		if (!facture.isTauxTvaMultiple()) {
			ajouterSiVide(aRenseigner, ChampFacture.TAUX_TVA, facture.getTauxTva());
		}
		ajouterSiVide(aRenseigner, ChampFacture.MONTANT_TTC, facture.getMontantTtc());
		ajouterSiVide(aRenseigner, ChampFacture.COMPTE_CONTREPARTIE, facture.getCompteContrepartie());
		String compte = facture.getCompteContrepartie();
		return new Lecture(statut, facture.getTypeTiers(), facture.getNomTiers(), facture.getNumeroFacture(),
				facture.getDateEmission(), facture.getMontantHt(), facture.getMontantTva(), facture.getTauxTva(),
				facture.isTauxTvaMultiple(), facture.getDetailTva().stream().map(TvaLue::enValeur).toList(),
				facture.getMontantTtc(), compte, compte == null ? null : AnalyseurFacture.LIBELLES_COMPTES.get(compte),
				aVerifier, List.copyOf(aRenseigner));
	}

	private static void ajouterSiVide(Set<ChampFacture> vides, ChampFacture champ, Object valeur) {
		if (valeur == null) {
			vides.add(champ);
		}
	}

}
