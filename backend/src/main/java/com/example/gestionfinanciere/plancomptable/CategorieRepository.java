package com.example.gestionfinanciere.plancomptable;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategorieRepository extends JpaRepository<Categorie, Long> {

	List<Categorie> findAllByOrderByOrdre();

}
