package com.example.gestionfinanciere.plancomptable;

// US-41 Compte du plan tel que l'écran l'affiche.
public record VueCompte(String numero, String libelle, boolean sousCompte, boolean actif) {

	public static VueCompte de(CompteComptable compte) {
		return new VueCompte(compte.getNumero(), compte.getLibelle(), compte.isSousCompte(), compte.isActif());
	}

}
