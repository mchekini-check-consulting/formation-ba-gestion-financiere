package com.example.gestionfinanciere.dossier;

import java.util.List;

/**
 * US-20 : comptables du cabinet, cochés s'ils suivent le dossier. modifiable : RG1, seuls
 * l'administrateur et les associés voient le bouton « Attribuer ».
 */
public record ComptablesDossier(List<Comptable> comptables, boolean modifiable) {

	public ComptablesDossier {
		comptables = List.copyOf(comptables);
	}

	public record Comptable(Long id, String nomAffiche, boolean attribue) {
	}

}
