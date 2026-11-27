package com.example.gestionfinanciere.ecriture;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EcritureRepository extends JpaRepository<Ecriture, Long> {

	// US-56 : l'écriture d'une facture (une au plus).
	Optional<Ecriture> findByFactureId(Long factureId);

	// US-30 : l'écriture d'une opération bancaire validée (une au plus).
	Optional<Ecriture> findByOperationId(Long operationId);

}
