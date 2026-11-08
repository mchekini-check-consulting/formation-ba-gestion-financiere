package com.example.gestionfinanciere.dossier;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DossierRepository extends JpaRepository<Dossier, Long> {

	// RG2 : SIREN déjà suivi par ce cabinet ?
	boolean existsByCabinetIdAndSiren(Long cabinetId, String siren);

	// Un cabinet ne voit jamais les dossiers d'un autre cabinet.
	Optional<Dossier> findByIdAndCabinetId(Long id, Long cabinetId);

}
