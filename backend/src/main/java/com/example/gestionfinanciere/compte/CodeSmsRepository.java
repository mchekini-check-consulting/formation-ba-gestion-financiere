package com.example.gestionfinanciere.compte;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeSmsRepository extends JpaRepository<CodeSms, Long> {

	Optional<CodeSms> findByEmpreinteDefi(String empreinteDefi);

}
