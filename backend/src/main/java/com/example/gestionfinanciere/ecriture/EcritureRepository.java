package com.example.gestionfinanciere.ecriture;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EcritureRepository extends JpaRepository<Ecriture, Long> {

	// US-56 : l'écriture d'une facture (une au plus).
	Optional<Ecriture> findByFactureId(Long factureId);

	// US-47 : dates des écritures d'un dossier, pour proposer les périodes du journal.
	@Query("select distinct e.dateEcriture from Ecriture e where e.dossierId = :dossierId")
	List<LocalDate> datesDuDossier(@Param("dossierId") Long dossierId);

	// US-47 RG1 : écritures d'un dossier sur une période, avec leurs lignes, dans l'ordre du journal.
	@EntityGraph(attributePaths = "lignes")
	List<Ecriture> findByDossierIdAndDateEcritureBetweenOrderByDateEcritureAscIdAsc(Long dossierId, LocalDate du,
			LocalDate au);

	// US-47 : une écriture du dossier.
	Optional<Ecriture> findByIdAndDossierId(Long id, Long dossierId);

}
