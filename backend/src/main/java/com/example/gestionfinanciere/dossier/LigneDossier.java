package com.example.gestionfinanciere.dossier;

import java.util.List;

// US-09 RG1 : une ligne de la liste des dossiers (raison sociale, SIREN, nombre de factures,
// état de l'espace client). US-20 : comptables qui suivent le dossier (noms affichés).
public record LigneDossier(Long id, String raisonSociale, String siren, long nombreFactures,
		EtatEspaceClient etatEspaceClient, List<String> comptables) {

	public LigneDossier {
		comptables = List.copyOf(comptables);
	}

	// Ligne sans comptable attribué.
	public LigneDossier(Long id, String raisonSociale, String siren, long nombreFactures,
			EtatEspaceClient etatEspaceClient) {
		this(id, raisonSociale, siren, nombreFactures, etatEspaceClient, List.of());
	}

	// Construit par la requête de DossierRepository.lister (comptages) ; comptables ajoutés ensuite.
	public LigneDossier(Long id, String raisonSociale, String siren, Long nombreFactures, Long comptesDirigeant,
			Long comptesActifs) {
		this(id, raisonSociale, siren, nombreFactures, EtatEspaceClient.de(comptesDirigeant, comptesActifs));
	}

	// US-20 : même ligne, avec ses comptables.
	LigneDossier avecComptables(List<String> noms) {
		return new LigneDossier(id, raisonSociale, siren, nombreFactures, etatEspaceClient, noms);
	}

}
