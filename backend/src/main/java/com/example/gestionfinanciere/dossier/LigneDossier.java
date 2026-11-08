package com.example.gestionfinanciere.dossier;

// US-09 RG1 : une ligne de la liste des dossiers (raison sociale, SIREN, nombre de factures,
// état de l'espace client).
public record LigneDossier(Long id, String raisonSociale, String siren, long nombreFactures,
		EtatEspaceClient etatEspaceClient) {

	// Construit par la requête de DossierRepository.lister (comptages).
	public LigneDossier(Long id, String raisonSociale, String siren, Long nombreFactures, Long comptesDirigeant,
			Long comptesActifs) {
		this(id, raisonSociale, siren, nombreFactures, EtatEspaceClient.de(comptesDirigeant, comptesActifs));
	}

}
