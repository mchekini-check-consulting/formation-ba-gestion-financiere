package com.example.gestionfinanciere.dossier;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DossierRepository extends JpaRepository<Dossier, Long> {

	// RG2 : SIREN déjà suivi par ce cabinet ?
	boolean existsByCabinetIdAndSiren(Long cabinetId, String siren);

	// Un cabinet ne voit jamais les dossiers d'un autre cabinet.
	Optional<Dossier> findByIdAndCabinetId(Long id, Long cabinetId);

	// US-11 RG1 : comptes du dirigeant du dossier (tous, puis activés) -> état de l'espace client.
	@Query("select count(c) from Compte c where c.dossierId = :dossierId")
	long compterComptesDirigeant(@Param("dossierId") Long dossierId);

	@Query("select count(c) from Compte c where c.dossierId = :dossierId and c.actif = true")
	long compterComptesDirigeantActifs(@Param("dossierId") Long dossierId);

	// US-20 RG2 : dossier attribué à ce comptable ?
	@Query("""
			select case when count(a) > 0 then true else false end from AttributionDossier a
			where a.dossier.id = :dossierId and a.comptable.id = :comptableId
			""")
	boolean estAttribue(@Param("dossierId") Long dossierId, @Param("comptableId") Long comptableId);

	/**
	 * US-09 : dossiers du cabinet, triés par raison sociale, avec le nombre de factures et les
	 * comptes du dirigeant (état de l'espace client). RG2 : {@code motif} filtre la raison
	 * sociale (« % » = tout), {@code motifSiren} le SIREN (« - » = jamais : un SIREN n'a que des chiffres).
	 * RG3 : toujours limité au cabinet. US-20 RG2 : sans {@code tous}, seulement les dossiers
	 * attribués au comptable {@code compteId}.
	 */
	@Query("""
			select new com.example.gestionfinanciere.dossier.LigneDossier(d.id, d.raisonSociale, d.siren,
			    (select count(f) from Facture f where f.dossier = d),
			    (select count(c) from Compte c where c.dossierId = d.id),
			    (select count(c) from Compte c where c.dossierId = d.id and c.actif = true))
			from Dossier d
			where d.cabinet.id = :cabinetId
			  and (lower(d.raisonSociale) like :motif or d.siren like :motifSiren)
			  and (:tous = true or exists (select a from AttributionDossier a
			                               where a.dossier = d and a.comptable.id = :compteId))
			order by lower(d.raisonSociale), d.id
			""")
	List<LigneDossier> lister(@Param("cabinetId") Long cabinetId, @Param("motif") String motif,
			@Param("motifSiren") String motifSiren, @Param("tous") boolean tous, @Param("compteId") Long compteId);

}
