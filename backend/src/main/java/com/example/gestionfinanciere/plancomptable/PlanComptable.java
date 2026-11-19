package com.example.gestionfinanciere.plancomptable;

import java.util.List;

// US-41 Plan comptable d'un dossier : ses comptes (RG1) et les comptes par défaut (RG2).
public record PlanComptable(List<VueCompte> comptes, List<CompteParDefaut> parDefaut) {

	// RG2 : compte utilisé par défaut pour une catégorie, ou pour la banque (code BANQUE).
	public record CompteParDefaut(String code, String libelle, String numero) {
	}

}
