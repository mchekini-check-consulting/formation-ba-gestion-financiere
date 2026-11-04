package com.example.gestionfinanciere.compte;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JetonReinitialisationRepository extends JpaRepository<JetonReinitialisation, Long> {

	Optional<JetonReinitialisation> findByEmpreinteJeton(String empreinteJeton);

}
