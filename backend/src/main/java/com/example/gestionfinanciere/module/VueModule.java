package com.example.gestionfinanciere.module;

// US-18 : un module du menu de gauche.
public record VueModule(String code, String nom, String description, ModuleDossier.Statut statut) {

	public static VueModule de(ModuleDossier module) {
		return new VueModule(module.getCode(), module.getNom(), module.getDescription(), module.getStatut());
	}

}
