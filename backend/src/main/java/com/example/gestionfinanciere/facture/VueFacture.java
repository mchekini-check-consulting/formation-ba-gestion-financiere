package com.example.gestionfinanciere.facture;

// US-13 : en-tête de la page d'aperçu (nom et type du fichier, onglet du dossier).
public record VueFacture(Long id, String nomFichier, String typeFichier, Facture.Sens sens) {

	static VueFacture de(Facture facture) {
		return new VueFacture(facture.getId(), facture.getNomFichier(), facture.getTypeFichier(), facture.getSens());
	}

}
