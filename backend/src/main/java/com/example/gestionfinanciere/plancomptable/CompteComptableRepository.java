package com.example.gestionfinanciere.plancomptable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CompteComptableRepository extends JpaRepository<CompteComptable, Long> {

	// Plan du dossier, trié par numéro (401 puis 401MOULINS puis 404).
	List<CompteComptable> findByDossierIdOrderByNumero(Long dossierId);

	Optional<CompteComptable> findByDossierIdAndNumero(Long dossierId, String numero);

	boolean existsByDossierIdAndNumero(Long dossierId, String numero);

	// RG3 : compte utilisé dans une écriture du dossier (US-56).
	@Query(nativeQuery = true, value = """
			select exists (select 1 from ligne_ecriture l join ecriture e on e.id = l.ecriture_id
			               where e.dossier_id = :dossierId and l.numero_compte = :numero)
			""")
	boolean utiliseDansUneEcriture(@Param("dossierId") Long dossierId, @Param("numero") String numero);

	// RG1 : le dossier qui vient d'être créé reçoit une copie du plan comptable général.
	@Transactional
	@Modifying
	@Query(nativeQuery = true, value = """
			insert into compte_comptable (dossier_id, numero, libelle, sous_compte, actif, cree_le)
			select :dossierId, numero, libelle, false, true, :creeLe from plan_comptable_general
			""")
	int initialiser(@Param("dossierId") Long dossierId, @Param("creeLe") Instant creeLe);

}
