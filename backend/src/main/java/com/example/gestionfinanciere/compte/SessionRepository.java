package com.example.gestionfinanciere.compte;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<SessionUtilisateur, Long> {

	Optional<SessionUtilisateur> findByEmpreinteJeton(String empreinteJeton);

	List<SessionUtilisateur> findByCompteAndFermeeFalse(Compte compte);

}
