package com.example.gestionfinanciere.module;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleRepository extends JpaRepository<ModuleDossier, Long> {

	// RG1 : tuiles dans l'ordre réglé en base.
	List<ModuleDossier> findAllByOrderByOrdreAscIdAsc();

}
