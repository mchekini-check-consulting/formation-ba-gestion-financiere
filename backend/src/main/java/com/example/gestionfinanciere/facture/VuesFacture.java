package com.example.gestionfinanciere.facture;

import org.springframework.stereotype.Component;

import com.example.gestionfinanciere.facture.lecture.AnalyseurFacture;
import com.example.gestionfinanciere.plancomptable.CompteComptable;
import com.example.gestionfinanciere.plancomptable.CompteComptableRepository;

/**
 * Fiche d'une facture (US-13, US-53, US-55) : champs lus et leurs doutes, libellé de la contrepartie
 * dans le plan comptable du dossier (US-41, à défaut celui du plan général) et dernière modification.
 */
@Component
public class VuesFacture {

	private final ChampLuRepository champsLus;
	private final CompteComptableRepository comptes;
	private final CorrectionFactureRepository corrections;

	public VuesFacture(ChampLuRepository champsLus, CompteComptableRepository comptes,
			CorrectionFactureRepository corrections) {
		this.champsLus = champsLus;
		this.comptes = comptes;
		this.corrections = corrections;
	}

	VueFacture vue(Facture facture) {
		String compte = facture.getCompteContrepartie();
		String libelle = compte == null ? null
				: comptes.findByDossierIdAndNumero(facture.getDossier().getId(), compte)
						.map(CompteComptable::getLibelle).orElseGet(() -> AnalyseurFacture.libelleCompte(compte));
		return VueFacture.de(facture, champsLus.findByFactureId(facture.getId()), libelle,
				corrections.findFirstByFactureIdOrderByModifieLeDescIdDesc(facture.getId()).orElse(null));
	}

}
