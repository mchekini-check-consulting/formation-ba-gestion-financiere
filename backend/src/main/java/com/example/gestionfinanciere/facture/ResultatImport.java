package com.example.gestionfinanciere.facture;

// US-52 Issue de l'import d'un fichier par le cabinet : facture importée, fichier refusé, ou
// dossier introuvable (d'un autre cabinet ou non attribué au comptable, RG3).
public record ResultatImport(Statut statut, LigneFacture facture) {

	public enum Statut {
		IMPORTEE,
		// RG2 (mêmes règles que l'US-15) : ni PDF, ni JPG, ni PNG (d'après son contenu).
		FORMAT_REFUSE,
		// RG2 : plus de 10 Mo.
		TROP_VOLUMINEUX,
		// RG3 : pas de droit sur le dossier.
		DOSSIER_INTROUVABLE
	}

	static ResultatImport importee(LigneFacture facture) {
		return new ResultatImport(Statut.IMPORTEE, facture);
	}

	static ResultatImport refuse(Statut statut) {
		return new ResultatImport(statut, null);
	}

}
