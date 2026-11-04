package com.example.gestionfinanciere.compte;

// État d'une session au moment d'une requête (US-07).
public record ResultatSession(Statut statut, Compte compte) {

	public enum Statut {
		ACTIVE,
		EXPIREE,
		NON_CONNECTE
	}

	static ResultatSession active(Compte compte) {
		return new ResultatSession(Statut.ACTIVE, compte);
	}

	static ResultatSession expiree() {
		return new ResultatSession(Statut.EXPIREE, null);
	}

	static ResultatSession nonConnecte() {
		return new ResultatSession(Statut.NON_CONNECTE, null);
	}

}
