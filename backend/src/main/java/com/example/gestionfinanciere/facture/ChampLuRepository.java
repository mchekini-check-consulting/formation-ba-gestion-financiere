package com.example.gestionfinanciere.facture;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ChampLuRepository extends JpaRepository<ChampLu, Long> {

	// US-53 RG4 : champs lus d'une facture (page de la facture).
	List<ChampLu> findByFactureId(Long factureId);

	// Une nouvelle lecture remplace la précédente.
	@Modifying
	@Query("delete from ChampLu c where c.facture.id = :factureId")
	void supprimerDeLaFacture(Long factureId);

}
