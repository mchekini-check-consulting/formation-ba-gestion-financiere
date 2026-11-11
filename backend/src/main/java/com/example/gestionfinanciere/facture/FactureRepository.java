package com.example.gestionfinanciere.facture;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FactureRepository extends JpaRepository<Facture, Long> {

	// US-12 : factures d'un onglet du dossier (achats ou ventes), les plus récentes d'abord.
	List<Facture> findByDossierIdAndSensOrderByDeposeeLeDescIdDesc(Long dossierId, Facture.Sens sens);

	// US-13 : une facture n'est lue qu'à travers son dossier (droits du dossier, US-11 RG3).
	Optional<Facture> findByIdAndDossierId(Long id, Long dossierId);

	// US-16 RG1 : factures déposées par le dirigeant dans un onglet, les plus récentes d'abord.
	List<Facture> findByDossierIdAndSensAndDeposeeParIdOrderByDeposeeLeDescIdDesc(Long dossierId, Facture.Sens sens,
			Long deposeeParId);

	// US-16 : le dirigeant ne supprime que ses propres dépôts.
	Optional<Facture> findByIdAndDossierIdAndDeposeeParId(Long id, Long dossierId, Long deposeeParId);

}
