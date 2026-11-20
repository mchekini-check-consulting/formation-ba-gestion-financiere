package com.example.gestionfinanciere.facture;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangementStatutFactureRepository extends JpaRepository<ChangementStatutFacture, Long> {

	// US-51 RG4 : changements de statut d'une facture, du plus ancien au plus récent.
	List<ChangementStatutFacture> findByFactureIdOrderByModifieLeAscIdAsc(Long factureId);

}
