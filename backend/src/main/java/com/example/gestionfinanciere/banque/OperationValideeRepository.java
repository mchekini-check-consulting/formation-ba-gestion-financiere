package com.example.gestionfinanciere.banque;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationValideeRepository extends JpaRepository<OperationValidee, Long> {

	// US-30 : une opération ne se valide qu'une fois par dossier.
	Optional<OperationValidee> findByDossierIdAndReference(Long dossierId, String reference);

	List<OperationValidee> findByDossierId(Long dossierId);

}
