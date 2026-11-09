package com.example.gestionfinanciere.equipe;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

	// US-14 RG2 : invitations encore valables d'un compte (ni utilisées, ni annulées).
	List<Invitation> findByCompteIdAndUtiliseeLeIsNullAndAnnuleeLeIsNull(Long compteId);

	// US-08 : invitation retrouvée par l'empreinte du jeton reçu dans le lien.
	Optional<Invitation> findByEmpreinteJeton(String empreinteJeton);

}
