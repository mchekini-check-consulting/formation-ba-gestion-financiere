package com.example.gestionfinanciere.facture;

import java.time.Instant;

/**
 * US-16 RG1 : une facture déposée par le dirigeant, avec sa date. supprimable : RG2 et RG3,
 * tant que personne du cabinet ne l'a ouverte, et US-51 RG5, tant qu'elle n'est pas « Traitée ».
 * US-51 CA1 et CA2 : son statut et, pour « À compléter », le motif.
 */
public record FactureDeposee(Long id, String nomFichier, Instant deposeeLe, Facture.Sens sens, boolean supprimable,
		Facture.Statut statut, String motif) {

	static FactureDeposee de(Facture facture) {
		return new FactureDeposee(facture.getId(), facture.getNomFichier(), facture.getDeposeeLe(), facture.getSens(),
				facture.getOuverteLe() == null && facture.getStatut() != Facture.Statut.TRAITEE, facture.getStatut(),
				facture.getMotifACompleter());
	}

}
