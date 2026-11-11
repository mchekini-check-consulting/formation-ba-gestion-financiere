package com.example.gestionfinanciere.facture;

// Issue du dépôt d'un fichier (US-15) : facture déposée, ou fichier refusé.
public record ResultatDepot(Statut statut, FactureDeposee facture) {

	public enum Statut {
		DEPOSEE,
		// RG1 : ni PDF, ni JPG, ni PNG (d'après son contenu).
		FORMAT_REFUSE,
		// RG1 : plus de 10 Mo.
		TROP_VOLUMINEUX
	}

	static ResultatDepot deposee(FactureDeposee facture) {
		return new ResultatDepot(Statut.DEPOSEE, facture);
	}

	static ResultatDepot refuse(Statut statut) {
		return new ResultatDepot(statut, null);
	}

}
