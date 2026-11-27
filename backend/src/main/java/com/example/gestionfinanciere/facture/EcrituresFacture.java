package com.example.gestionfinanciere.facture;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.ecriture.CalculEcriture;
import com.example.gestionfinanciere.ecriture.Ecriture;
import com.example.gestionfinanciere.ecriture.EcritureService;
import com.example.gestionfinanciere.ecriture.PieceAchat;
import com.example.gestionfinanciere.ecriture.VueEcriture;
import com.example.gestionfinanciere.facture.lecture.TvaParTaux;

/**
 * US-56 : écriture d'une facture d'achat ; US-57 : d'une facture de vente (sens inversé). Calculée à partir des
 * informations de la facture : RG4, une ligne de TVA par taux pour une facture à plusieurs taux
 * (US-54 CA2) dont le détail fait bien le montant de TVA ; sinon une seule ligne de TVA.
 */
@Component
public class EcrituresFacture {

	private final EcritureService ecritures;
	private final CorrectionFactureRepository corrections;

	public EcrituresFacture(EcritureService ecritures, CorrectionFactureRepository corrections) {
		this.ecritures = ecritures;
		this.corrections = corrections;
	}

	// Journal de la facture : achats (US-56) ou ventes (US-57).
	static Ecriture.Journal journal(Facture facture) {
		return facture.getSens() == Facture.Sens.VENTE ? Ecriture.Journal.VT : Ecriture.Journal.AC;
	}

	// Informations à enregistrer sur la facture (valeurs) -> écriture ; vide s'il manque une information.
	static Optional<CalculEcriture.Resultat> calcul(Facture facture, InformationsFacture valeurs) {
		if (valeurs.nomTiers() == null || valeurs.numeroFacture() == null || valeurs.dateEmission() == null
				|| valeurs.montantHt() == null || valeurs.montantTva() == null || valeurs.montantTtc() == null
				|| valeurs.compteContrepartie() == null) {
			return Optional.empty();
		}
		PieceAchat piece = new PieceAchat(valeurs.nomTiers(), valeurs.numeroFacture(), valeurs.dateEmission(),
				valeurs.montantHt(), tva(facture, valeurs), valeurs.montantTtc(), valeurs.compteContrepartie());
		return Optional.of(journal(facture) == Ecriture.Journal.VT ? CalculEcriture.vente(piece)
				: CalculEcriture.achat(piece));
	}

	static Optional<CalculEcriture.Resultat> calcul(Facture facture) {
		return calcul(facture, informations(facture));
	}

	// Un taux saisi remplace le détail par taux (Facture.corriger) : une seule ligne de TVA.
	private static List<PieceAchat.TvaDuTaux> tva(Facture facture, InformationsFacture valeurs) {
		List<TvaParTaux> detail = facture.getDetailTva().stream().map(TvaLue::enValeur).toList();
		boolean parTaux = valeurs.tauxTva() == null && facture.isTauxTvaMultiple() && !detail.isEmpty()
				&& detail.stream().map(TvaParTaux::montantTva).reduce(BigDecimal.ZERO, BigDecimal::add)
						.compareTo(valeurs.montantTva()) == 0;
		if (!parTaux) {
			return List.of(new PieceAchat.TvaDuTaux(valeurs.tauxTva(), valeurs.montantTva()));
		}
		return detail.stream().map(t -> new PieceAchat.TvaDuTaux(t.taux(), t.montantTva())).toList();
	}

	private static InformationsFacture informations(Facture facture) {
		return new InformationsFacture(facture.getTypeTiers(), facture.getNomTiers(), facture.getNumeroFacture(),
				facture.getDateEmission(), facture.getMontantHt(), facture.getMontantTva(), facture.getTauxTva(),
				facture.getMontantTtc(), facture.getCompteContrepartie());
	}

	// Refus de l'écriture qu'on obtiendrait avec ces informations (RG2, comptes du plan) ; vide si elle passe.
	Optional<String> refus(Facture facture, InformationsFacture valeurs) {
		return calcul(facture, valeurs).flatMap(calcul -> ecritures.refus(facture.getDossier().getId(), calcul));
	}

	boolean exportee(Facture facture) {
		return ecritures.deLaFacture(facture.getId()).map(Ecriture::isExportee).orElse(false);
	}

	// RG1, RG5 : écriture (re)générée à partir des informations enregistrées de la facture.
	void generer(Facture facture, Compte auteur, Instant quand) {
		calcul(facture).ifPresent(calcul -> ecritures.enregistrer(facture.getDossier().getId(), facture.getId(),
				journal(facture), calcul, auteur, quand));
	}

	/**
	 * RG6 : bloc « Écritures générées » de la fiche ; vide tant qu'aucune écriture n'est générée.
	 * US-47 : une écriture corrigée à la main n'est « À re-générer » que si la facture a été corrigée depuis.
	 */
	Optional<VueEcriture> vue(Facture facture) {
		if (facture.getId() == null) {
			return Optional.empty();
		}
		return ecritures.deLaFacture(facture.getId()).map(ecriture -> ecriture.isModifiee()
				? ecritures.vue(ecriture,
						!corrections.existsByFactureIdAndModifieLeAfter(facture.getId(), ecriture.getModifieeLe()))
				: ecritures.vue(ecriture, calcul(facture).orElse(null)));
	}

}
