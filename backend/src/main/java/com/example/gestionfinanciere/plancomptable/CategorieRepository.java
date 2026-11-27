package com.example.gestionfinanciere.plancomptable;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategorieRepository extends JpaRepository<Categorie, Long> {

	List<Categorie> findAllByOrderByOrdre();

	// US-30 : catégorie choisie à la validation d'une opération bancaire.
	Optional<Categorie> findByCode(String code);

}
