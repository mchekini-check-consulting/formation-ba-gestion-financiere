package com.example.gestionfinanciere.facture;

/**
 * US-53 / US-54 : ce qu'il faut pour lire une facture : son fichier, son sens (fournisseur ou
 * client) et le nom de l'entreprise du dossier, qui émet les factures clients (US-54 RG3).
 */
public record FactureALire(FichierFacture fichier, Facture.Sens sens, String entrepriseDuDossier) {

	static FactureALire de(Facture facture) {
		return new FactureALire(FichierFacture.de(facture), facture.getSens(),
				facture.getDossier().getRaisonSociale());
	}

}
