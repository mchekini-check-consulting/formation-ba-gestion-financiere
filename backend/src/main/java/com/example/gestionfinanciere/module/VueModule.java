package com.example.gestionfinanciere.module;

// US-18 : une tuile de l'onglet Modules du dossier.
public record VueModule(String code, String nom, String description, ModuleDossier.Statut statut) {

	public static VueModule de(ModuleDossier module) {
		return new VueModule(module.getCode(), module.getNom(), module.getDescription(), module.getStatut());
	}

}
