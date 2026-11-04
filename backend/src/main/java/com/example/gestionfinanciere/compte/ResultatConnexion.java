package com.example.gestionfinanciere.compte;

// Issue de la vérification email + mot de passe (US-03, US-05).
public record ResultatConnexion(Statut statut, Compte compte) {

	public enum Statut {
		REUSSIE,
		INCORRECTE,
		BLOQUEE
	}

	static ResultatConnexion reussie(Compte compte) {
		return new ResultatConnexion(Statut.REUSSIE, compte);
	}

	static ResultatConnexion incorrecte() {
		return new ResultatConnexion(Statut.INCORRECTE, null);
	}

	static ResultatConnexion bloquee() {
		return new ResultatConnexion(Statut.BLOQUEE, null);
	}

}
