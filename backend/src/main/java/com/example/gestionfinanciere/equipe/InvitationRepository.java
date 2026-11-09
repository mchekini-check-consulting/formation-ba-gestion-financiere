package com.example.gestionfinanciere.equipe;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

	// US-14 RG2 : invitations encore valables d'un compte (ni utilisées, ni annulées).
	List<Invitation> findByCompteIdAndUtiliseeLeIsNullAndAnnuleeLeIsNull(Long compteId);

}
