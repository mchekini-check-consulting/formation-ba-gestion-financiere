package com.example.gestionfinanciere.equipe;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.RoleCabinet;

// Une ligne de l'écran Équipe (US-19). statut : ACTIF, ou INVITE tant que le compte n'est pas activé (US-08).
public record Membre(Long id, String nomAffiche, String email, RoleCabinet role, Statut statut) {

	public enum Statut {
		ACTIF,
		INVITE
	}

	static Membre de(Compte compte) {
		return new Membre(compte.getId(), compte.getNomAffiche(), compte.getEmail(), compte.getRoleCabinet(),
				compte.isActif() ? Statut.ACTIF : Statut.INVITE);
	}

}
