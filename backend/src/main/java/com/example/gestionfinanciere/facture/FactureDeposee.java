package com.example.gestionfinanciere.facture;

import java.time.Instant;

/**
 * US-16 RG1 : une facture déposée par le dirigeant, avec sa date. supprimable : RG2 et RG3,
 * tant que personne du cabinet ne l'a ouverte.
 */
public record FactureDeposee(Long id, String nomFichier, Instant deposeeLe, Facture.Sens sens, boolean supprimable) {

	static FactureDeposee de(Facture facture) {
		return new FactureDeposee(facture.getId(), facture.getNomFichier(), facture.getDeposeeLe(), facture.getSens(),
				facture.getOuverteLe() == null);
	}

}
