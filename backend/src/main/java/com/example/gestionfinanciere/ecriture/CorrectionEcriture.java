package com.example.gestionfinanciere.ecriture;

import java.math.BigDecimal;
import java.util.List;

/**
 * US-47 RG2 : correction demandée depuis le journal : libellé de l'écriture, et pour chaque ligne,
 * dans l'ordre, son compte, son débit et son crédit. La date et le n° de pièce restent ceux de la facture.
 */
public record CorrectionEcriture(String libelle, List<LigneCorrigee> lignes) {

	public record LigneCorrigee(String numeroCompte, BigDecimal debit, BigDecimal credit) {
	}

}
