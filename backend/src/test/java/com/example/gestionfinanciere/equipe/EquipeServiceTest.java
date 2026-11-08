package com.example.gestionfinanciere.equipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.cabinet.ValidationInscription;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.CompteRepository;
import com.example.gestionfinanciere.compte.HorlogeReglable;
import com.example.gestionfinanciere.compte.Jetons;
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.compte.RoleCabinet;
import com.example.gestionfinanciere.notification.EnvoiEmail;

class EquipeServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-06T09:00:00Z");

	private final PasswordEncoder encodeur = new BCryptPasswordEncoder(4);
	private final CompteRepository comptes = mock(CompteRepository.class);
	private final InvitationRepository invitations = mock(InvitationRepository.class);
	private final EnvoiEmail emails = mock(EnvoiEmail.class);
	private final EquipeService service = new EquipeService(comptes, invitations, encodeur,
			new HorlogeReglable(MAINTENANT), emails, "https://frontend.test", true);

	private final Cabinet cabinetTest = new Cabinet("Cabinet Test", "123456789", 120, null, MAINTENANT, MAINTENANT);
	private final Compte claire = Compte.administrateurDe(cabinetTest, "claire.martin@cabinet-test.fr", "x",
			"Claire Martin");

	// CA1 : Claire invite Lucie Moreau comme Comptable.
	static DemandeInvitation lucie() {
		return new DemandeInvitation("Lucie", "Moreau", " Lucie.Moreau@Cabinet-Test.fr ", "COMPTABLE");
	}

	@BeforeEach
	void base() {
		when(comptes.findByEmail(anyString())).thenReturn(Optional.empty());
		when(comptes.save(any(Compte.class))).thenAnswer(i -> i.getArgument(0));
		when(invitations.save(any(Invitation.class))).thenAnswer(i -> i.getArgument(0));
	}

	@Test
	void ca1LucieApparaitInviteeEtRecoitLEmail() {
		ResultatInvitation resultat = service.inviter(claire, lucie());

		assertEquals(ResultatInvitation.Statut.ENVOYEE, resultat.statut());
		assertEquals(new Membre(null, "Lucie Moreau", "lucie.moreau@cabinet-test.fr", RoleCabinet.COMPTABLE,
				Membre.Statut.INVITE), resultat.membre());

		ArgumentCaptor<Compte> compte = ArgumentCaptor.forClass(Compte.class);
		verify(comptes).save(compte.capture());
		Compte lucie = compte.getValue();
		assertFalse(lucie.isActif()); // activé plus tard depuis le lien (US-08)
		assertSame(cabinetTest, lucie.getCabinet());
		assertEquals(Role.COLLABORATEUR, lucie.getRole());
		assertFalse(lucie.isAdministrateur());

		ArgumentCaptor<Invitation> invitation = ArgumentCaptor.forClass(Invitation.class);
		verify(invitations).save(invitation.capture());
		assertSame(lucie, invitation.getValue().getCompte());
		assertSame(claire, invitation.getValue().getInviteePar());
		assertEquals(MAINTENANT, invitation.getValue().getEnvoyeeLe());
		assertEquals(MAINTENANT.plus(Duration.ofDays(7)), invitation.getValue().getExpireLe());
		assertNull(invitation.getValue().getUtiliseeLe());
		assertEquals(Jetons.empreinte(resultat.jetonDeTest()), invitation.getValue().getEmpreinteJeton());

		verify(emails).envoyer(eq("lucie.moreau@cabinet-test.fr"),
				eq("Claire Martin vous invite à rejoindre Cabinet Test sur Pointéo"),
				contains("https://frontend.test/activer-compte?jeton=" + resultat.jetonDeTest()));
	}

	@Test
	void horsModeTestLeLienNEstPasRenvoye() {
		EquipeService production = new EquipeService(comptes, invitations, encodeur, new HorlogeReglable(MAINTENANT),
				emails, "https://frontend.test", false);
		assertNull(production.inviter(claire, lucie()).jetonDeTest());
	}

	@Test
	void ca2EmailDejaUtiliseRefuse() {
		when(comptes.findByEmail("sophie.leroy@boulangerie-leroy.fr")).thenReturn(Optional.of(mock(Compte.class)));
		ResultatInvitation resultat = service.inviter(claire,
				new DemandeInvitation("Sophie", "Leroy", "Sophie.Leroy@boulangerie-leroy.fr", "ASSOCIE"));
		assertEquals(ResultatInvitation.Statut.EMAIL_DEJA_UTILISE, resultat.statut());
		verify(comptes, never()).save(any());
		verify(emails, never()).envoyer(anyString(), anyString(), anyString());
	}

	@Test
	void formulaireInvalideRienNEstEnvoye() {
		ResultatInvitation resultat = service.inviter(claire, new DemandeInvitation("", "Moreau", "lucie", "CHEF"));
		assertEquals(ResultatInvitation.Statut.INVALIDE, resultat.statut());
		assertEquals(Map.of("prenom", ValidationInscription.OBLIGATOIRE, "email", ValidationInscription.EMAIL_INVALIDE,
				"role", ValidationInvitation.ROLE_INVALIDE), resultat.erreurs());
		verify(comptes, never()).save(any());
	}

	@Test
	void listeDesMembresDuCabinet() {
		Compte lucie = Compte.invite(cabinetTest, "lucie.moreau@cabinet-test.fr", "x", "Lucie Moreau",
				RoleCabinet.COMPTABLE);
		when(comptes.findByCabinetIdOrderByNomAfficheAsc(cabinetTest.getId())).thenReturn(List.of(claire, lucie));
		List<Membre> membres = service.membres(claire);
		assertEquals(List.of(
				new Membre(null, "Claire Martin", "claire.martin@cabinet-test.fr", RoleCabinet.ADMINISTRATEUR,
						Membre.Statut.ACTIF),
				new Membre(null, "Lucie Moreau", "lucie.moreau@cabinet-test.fr", RoleCabinet.COMPTABLE,
						Membre.Statut.INVITE)),
				membres);
	}

	@Test
	void validationDesChamps() {
		assertTrue(ValidationInvitation.erreurs(lucie()).isEmpty());
		Map<String, String> e = ValidationInvitation
				.erreurs(new DemandeInvitation("P".repeat(101), null, null, null));
		assertEquals(Map.of("prenom", ValidationInscription.TROP_LONG, "nom", ValidationInscription.OBLIGATOIRE,
				"email", ValidationInscription.OBLIGATOIRE, "role", ValidationInvitation.ROLE_INVALIDE), e);
		assertEquals(RoleCabinet.ASSOCIE, ValidationInvitation.role("ASSOCIE"));
		assertEquals(RoleCabinet.ADMINISTRATEUR, ValidationInvitation.role("ADMINISTRATEUR"));
		assertNull(ValidationInvitation.role("associe"));
	}

	@Test
	void invitationEtCompteInvite() {
		Compte lucie = Compte.invite(cabinetTest, "l@test.fr", "x", "Lucie Moreau", RoleCabinet.ASSOCIE);
		Invitation invitation = new Invitation(lucie, claire, "empreinte", MAINTENANT, MAINTENANT.plusSeconds(1));
		assertNull(invitation.getId());
		assertFalse(lucie.isActif()); // US-08 : activé plus tard depuis le lien reçu par email
		assertEquals(RoleCabinet.ASSOCIE, lucie.getRoleCabinet());
		assertEquals(RoleCabinet.ADMINISTRATEUR, claire.getRoleCabinet());
	}

}
