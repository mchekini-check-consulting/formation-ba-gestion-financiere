package com.example.gestionfinanciere.dossier;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttributionDossierRepository extends JpaRepository<AttributionDossier, Long> {

	// US-20 : comptables d'un dossier.
	List<AttributionDossier> findByDossierId(Long dossierId);

	// US-20 : comptables de tous les dossiers d'un cabinet (colonne « Comptables » de la liste).
	@Query("""
			select a from AttributionDossier a
			where a.dossier.cabinet.id = :cabinetId
			order by a.comptable.nomAffiche, a.id
			""")
	List<AttributionDossier> findByCabinetId(@Param("cabinetId") Long cabinetId);

	// US-20 RG1 : nouvelle attribution d'un dossier, qui remplace la précédente.
	@Modifying
	@Query("delete from AttributionDossier a where a.dossier.id = :dossierId")
	void supprimerPourDossier(@Param("dossierId") Long dossierId);

}
