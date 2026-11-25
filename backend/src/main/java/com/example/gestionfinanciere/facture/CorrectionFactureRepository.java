package com.example.gestionfinanciere.facture;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CorrectionFactureRepository extends JpaRepository<CorrectionFacture, Long> {

	// US-55 RG8 : dernière modification, affichée sur la fiche (« Modifiée par … le … »).
	Optional<CorrectionFacture> findFirstByFactureIdOrderByModifieLeDescIdDesc(Long factureId);

}
