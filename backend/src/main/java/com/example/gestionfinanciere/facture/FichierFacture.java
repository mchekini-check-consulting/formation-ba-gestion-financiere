package com.example.gestionfinanciere.facture;

import java.util.Arrays;
import java.util.Objects;

// US-13 RG2 : fichier d'origine de la facture, renvoyé tel qu'il a été déposé.
public record FichierFacture(String nomFichier, String typeFichier, byte[] contenu) {

	public FichierFacture {
		contenu = contenu.clone();
	}

	static FichierFacture de(Facture facture) {
		return new FichierFacture(facture.getNomFichier(), facture.getTypeFichier(), facture.getContenu());
	}

	@Override
	public byte[] contenu() {
		return contenu.clone();
	}

	@Override
	public boolean equals(Object autre) {
		return autre instanceof FichierFacture fichier && nomFichier.equals(fichier.nomFichier)
				&& typeFichier.equals(fichier.typeFichier) && Arrays.equals(contenu, fichier.contenu);
	}

	@Override
	public int hashCode() {
		return Objects.hash(nomFichier, typeFichier, Arrays.hashCode(contenu));
	}

	// Jamais le contenu de la facture (logs, messages d'erreur).
	@Override
	public String toString() {
		return "FichierFacture[" + typeFichier + ", " + contenu.length + " octets]";
	}

}
