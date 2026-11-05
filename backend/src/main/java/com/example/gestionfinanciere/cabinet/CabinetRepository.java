package com.example.gestionfinanciere.cabinet;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CabinetRepository extends JpaRepository<Cabinet, Long> {

	// RG2 : le SIREN est stocké sans espaces (9 chiffres).
	boolean existsBySiren(String siren);

}
