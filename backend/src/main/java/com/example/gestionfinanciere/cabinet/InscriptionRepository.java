package com.example.gestionfinanciere.cabinet;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InscriptionRepository extends JpaRepository<Inscription, Long> {

	Optional<Inscription> findByEmpreinteDefi(String empreinteDefi);

}
