package com.example.gestionfinanciere.dossier;

import java.util.Optional;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.RoleCabinet;

/**
 * US-20 Droits d'un collaborateur sur les dossiers de son cabinet, utilisés par tout ce qui
 * s'ouvre depuis un dossier (en-tête, factures, modules, invitation du dirigeant).
 * RG1 : l'administrateur et les associés voient tous les dossiers du cabinet et les attribuent.
 * RG2 : un comptable ne voit que les dossiers qui lui sont attribués, même en tapant l'adresse
 * d'un autre (même réponse qu'un dossier inexistant, US-11 RG3).
 */
public final class AccesDossier {

	private AccesDossier() {
	}

	// RG1 : administrateur ou associé.
	public static boolean voitTousLesDossiers(Compte collaborateur) {
		RoleCabinet role = collaborateur.getRoleCabinet();
		return role == RoleCabinet.ADMINISTRATEUR || role == RoleCabinet.ASSOCIE;
	}

	// RG1 : seuls ceux qui voient tous les dossiers les attribuent.
	public static boolean peutAttribuer(Compte collaborateur) {
		return voitTousLesDossiers(collaborateur);
	}

	// Dossier du cabinet du collaborateur et, pour un comptable, qui lui est attribué (RG2) ; sinon vide.
	public static Optional<Dossier> dossierAccessible(DossierRepository dossiers, Compte collaborateur,
			Long dossierId) {
		Optional<Dossier> dossier = dossiers.findByIdAndCabinetId(dossierId, collaborateur.getCabinet().getId());
		if (voitTousLesDossiers(collaborateur)) {
			return dossier;
		}
		return dossier.filter(trouve -> dossiers.estAttribue(trouve.getId(), collaborateur.getId()));
	}

}
