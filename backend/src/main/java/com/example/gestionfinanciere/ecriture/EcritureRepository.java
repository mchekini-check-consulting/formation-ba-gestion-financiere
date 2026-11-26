package com.example.gestionfinanciere.ecriture;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EcritureRepository extends JpaRepository<Ecriture, Long> {

	// US-56 : l'écriture d'une facture (une au plus).
	Optional<Ecriture> findByFactureId(Long factureId);

}
