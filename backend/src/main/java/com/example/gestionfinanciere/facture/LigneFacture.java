package com.example.gestionfinanciere.facture;

import java.time.Instant;

// US-12 RG2 : une ligne de la liste des factures d'un dossier ; RG3 : nouvelle tant que personne
// du cabinet ne l'a ouverte. US-52 RG4 : source (dépôt du dirigeant ou import du cabinet).
public record LigneFacture(Long id, String nomFichier, Instant deposeeLe, String deposeePar, Facture.Statut statut,
		boolean nouvelle, Facture.Source source) {

	static LigneFacture de(Facture facture) {
		return new LigneFacture(facture.getId(), facture.getNomFichier(), facture.getDeposeeLe(),
				facture.getDeposeePar().getNomAffiche(), facture.getStatut(), facture.getOuverteLe() == null,
				facture.getSource());
	}

}
