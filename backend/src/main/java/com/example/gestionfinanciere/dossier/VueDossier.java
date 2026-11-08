package com.example.gestionfinanciere.dossier;

import java.time.Instant;

// Dossier tel qu'affiché par l'écran (US-10).
public record VueDossier(Long id, String raisonSociale, String siren, Instant creeLe) {

	static VueDossier de(Dossier dossier) {
		return new VueDossier(dossier.getId(), dossier.getRaisonSociale(), dossier.getSiren(), dossier.getCreeLe());
	}

}
