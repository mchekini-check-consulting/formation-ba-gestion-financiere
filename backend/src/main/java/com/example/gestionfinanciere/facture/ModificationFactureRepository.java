package com.example.gestionfinanciere.facture;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ModificationFactureRepository extends JpaRepository<ModificationFacture, Long> {

	// US-55 RG8 : historique des modifications d'une facture, dans l'ordre.
	List<ModificationFacture> findByFactureIdOrderById(Long factureId);

}
