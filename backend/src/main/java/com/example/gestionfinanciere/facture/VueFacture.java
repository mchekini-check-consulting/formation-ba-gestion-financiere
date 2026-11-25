package com.example.gestionfinanciere.facture;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.example.gestionfinanciere.facture.lecture.AnalyseurFacture;
import com.example.gestionfinanciere.facture.lecture.TvaParTaux;

// US-13 : en-tête de la page d'aperçu (nom et type du fichier, onglet du dossier).
// US-53 : résultat de la lecture automatique ; vide pour une facture jamais lue.
// US-55 : statut de traitement (une facture « Traitée » ne se modifie plus) et dernière modification (RG8).
public record VueFacture(Long id, String nomFichier, String typeFichier, Facture.Sens sens, Facture.Statut statut,
		Lecture lecture, Modification modification) {

	/**
	 * US-53 RG2 à RG6 : statut et champs lus. aVerifier : lus avec un doute (RG4, RG5) ; aRenseigner :
	 * non lus (RG3). Champs vides tant que la lecture est en cours. US-54 CA2 : tauxMultiple (taux
	 * affiché « Multiple ») et TVA par taux, du plus haut au plus bas. US-55 RG7 : après une lecture
	 * échouée, champs saisis à la main.
	 */
	public record Lecture(Facture.StatutLecture statut, String typeTiers, String nomTiers, String numeroFacture,
			LocalDate dateEmission, BigDecimal montantHt, BigDecimal montantTva, BigDecimal tauxTva,
			boolean tauxMultiple, List<TvaParTaux> detailTva, BigDecimal montantTtc, String compteContrepartie,
			String libelleContrepartie, List<ChampFacture> aVerifier, List<ChampFacture> aRenseigner) {
	}

	// US-55 RG8 : dernière modification des informations (nom affiché de l'auteur et date).
	public record Modification(String par, Instant le) {
	}

	static VueFacture de(Facture facture, List<ChampLu> champsLus) {
		String compte = facture.getCompteContrepartie();
		return de(facture, champsLus, compte == null ? null : AnalyseurFacture.libelleCompte(compte), null);
	}

	// libelleContrepartie : libellé du compte dans le plan comptable du dossier (US-41).
	static VueFacture de(Facture facture, List<ChampLu> champsLus, String libelleContrepartie,
			CorrectionFacture derniere) {
		Modification modification = derniere == null ? null
				: new Modification(derniere.getModifiePar().getNomAffiche(), derniere.getModifieLe());
		return new VueFacture(facture.getId(), facture.getNomFichier(), facture.getTypeFichier(), facture.getSens(),
				facture.getStatut(), lecture(facture, champsLus, libelleContrepartie), modification);
	}

	private static Lecture lecture(Facture facture, List<ChampLu> champsLus, String libelleContrepartie) {
		Facture.StatutLecture statut = facture.getLectureStatut();
		if (statut == null) {
			return null;
		}
		if (statut == Facture.StatutLecture.EN_COURS) {
			return new Lecture(statut, null, null, null, null, null, null, null, false, List.of(), null, null, null,
					List.of(), List.of());
		}
		List<ChampFacture> aVerifier = champsLus.stream().filter(ChampLu::isAVerifier).map(ChampLu::getChamp).sorted()
				.toList();
		return new Lecture(statut, facture.getTypeTiers(), facture.getNomTiers(), facture.getNumeroFacture(),
				facture.getDateEmission(), facture.getMontantHt(), facture.getMontantTva(), facture.getTauxTva(),
				facture.isTauxTvaMultiple(), facture.getDetailTva().stream().map(TvaLue::enValeur).toList(),
				facture.getMontantTtc(), facture.getCompteContrepartie(),
				facture.getCompteContrepartie() == null ? null : libelleContrepartie, aVerifier,
				aRenseigner(facture));
	}

	// US-53 RG3 : champs non renseignés ; le taux d'une facture à plusieurs taux n'en fait pas partie.
	static List<ChampFacture> aRenseigner(Facture facture) {
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
		return List.copyOf(aRenseigner);
	}

	private static void ajouterSiVide(Set<ChampFacture> vides, ChampFacture champ, Object valeur) {
		if (valeur == null) {
			vides.add(champ);
		}
	}

}
