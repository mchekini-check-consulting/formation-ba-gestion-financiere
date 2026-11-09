package com.example.gestionfinanciere.dossier;

import java.time.Instant;

// Dossier tel qu'affiché par l'écran (US-10) ; US-11 RG1 : en-tête avec l'état de l'espace client.
public record VueDossier(Long id, String raisonSociale, String siren, Instant creeLe,
		EtatEspaceClient etatEspaceClient) {

	// Dossier qui vient d'être créé : personne n'est encore invité.
	static VueDossier de(Dossier dossier) {
		return de(dossier, EtatEspaceClient.NON_INVITE);
	}

	static VueDossier de(Dossier dossier, EtatEspaceClient etat) {
		return new VueDossier(dossier.getId(), dossier.getRaisonSociale(), dossier.getSiren(), dossier.getCreeLe(),
				etat);
	}

}
