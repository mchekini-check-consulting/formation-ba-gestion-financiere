package com.example.gestionfinanciere.compte;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompteRepository extends JpaRepository<Compte, Long> {

	// L'email est stocké en minuscules (V5__compte.sql) : on cherche avec l'email normalisé.
	Optional<Compte> findByEmail(String email);

}
