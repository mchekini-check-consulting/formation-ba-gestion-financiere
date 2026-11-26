package com.example.gestionfinanciere.ecriture;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * US-56 : informations d'une facture d'achat validée, d'où l'écriture est calculée (CalculEcriture).
 * tva : une entrée par taux (RG4) ; le taux est vide s'il n'est pas connu.
 */
public record PieceAchat(String nomTiers, String numeroFacture, LocalDate dateEmission, BigDecimal montantHt,
		List<TvaDuTaux> tva, BigDecimal montantTtc, String compteContrepartie) {

	public PieceAchat {
		tva = List.copyOf(tva);
	}

	// TVA d'un taux (taux vide : non connu).
	public record TvaDuTaux(BigDecimal taux, BigDecimal montant) {
	}

}
