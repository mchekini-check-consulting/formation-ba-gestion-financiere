package com.example.gestionfinanciere.activation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.gestionfinanciere.activation.ResultatActivation.Statut;
import com.example.gestionfinanciere.cabinet.ValidationInscription;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.HorlogeReglable;
import com.example.gestionfinanciere.compte.Jetons;
import com.example.gestionfinanciere.compte.RegleMotDePasse;
import com.example.gestionfinanciere.equipe.Invitation;
import com.example.gestionfinanciere.equipe.InvitationRepository;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.JournalCapture;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

class ActivationServiceTest {

	private static final String JETON = "jeton-sophie";
	private static final String MOT_DE_PASSE = "Sophie#Pointeo2026";
	private static final Instant ENVOI = Instant.parse("2026-11-01T09:00:00Z");

	private final PasswordEncoder encodeur = new BCryptPasswordEncoder(4);
	private final InvitationRepository invitations = mock(InvitationRepository.class);
	private final HorlogeReglable horloge = new HorlogeReglable(ENVOI.plus(Duration.ofDays(1)));
	private final ActivationService service = new ActivationService(invitations, encodeur, horloge);

	private final Compte sophie = Compte.dirigeantInvite(42L, "sophie@test.fr", "inutilisable", "Sophie Martin");
	private final Invitation invitation = new Invitation(sophie, null, Jetons.empreinte(JETON), ENVOI,
			ENVOI.plus(Duration.ofDays(7)));

	@BeforeEach
	void base() {
		when(invitations.findByEmpreinteJeton(anyString())).thenReturn(Optional.empty());
		when(invitations.findByEmpreinteJeton(Jetons.empreinte(JETON))).thenReturn(Optional.of(invitation));
	}

	@Test
	void rg1LienValablePendantSeptJours() {
		ResultatActivation resultat = service.verifier(JETON);
		assertEquals(Statut.VALABLE, resultat.statut());
		assertEquals(sophie, resultat.compte());

		horloge.avancer(Duration.ofDays(6).minusSeconds(1));
		assertEquals(Statut.VALABLE, service.verifier(JETON).statut());
		horloge.avancer(Duration.ofSeconds(1));
		assertEquals(Statut.EXPIREE, service.verifier(JETON).statut());
		assertEquals(Statut.EXPIREE, service.activer(JETON, MOT_DE_PASSE, true).statut());
		assertFalse(sophie.isActif());
	}

	@Test
	void rg1LienInconnuVideOuAnnuleParUnRenvoi() {
		assertEquals(Statut.EXPIREE, service.verifier("autre").statut());
		assertNull(service.verifier("autre").compte());
		assertEquals(Statut.EXPIREE, service.verifier(null).statut());
		assertEquals(Statut.EXPIREE, service.verifier("  ").statut());
		invitation.annuler(horloge.instant());
		assertEquals(Statut.EXPIREE, service.verifier(JETON).statut());
	}

	@Test
	void rg2EtRg3MotDePasseChoisiEtCompteActifAussitot() {
		ResultatActivation resultat = service.activer(JETON, MOT_DE_PASSE, true);
		assertEquals(Statut.ACTIVE, resultat.statut());
		assertEquals(sophie, resultat.compte());
		assertTrue(sophie.isActif());
		assertTrue(encodeur.matches(MOT_DE_PASSE, sophie.getMotDePasseHash()));
		assertEquals(horloge.instant(), invitation.getUtiliseeLe());
	}

	@Test
	void rg1UneSeuleFois() {
		service.activer(JETON, MOT_DE_PASSE, true);
		assertEquals(Statut.DEJA_ACTIVE, service.verifier(JETON).statut());
		assertEquals(Statut.DEJA_ACTIVE, service.activer(JETON, "Autre#Pointeo2026", true).statut());
		assertTrue(encodeur.matches(MOT_DE_PASSE, sophie.getMotDePasseHash()));
	}

	@Test
	void compteDejaActifParUnAutreLien() {
		sophie.activer("empreinte");
		assertEquals(Statut.DEJA_ACTIVE, service.verifier(JETON).statut());
	}

	@Test
	void rg2CguObligatoiresEtReglesDuMotDePasse() {
		ResultatActivation sansCgu = service.activer(JETON, MOT_DE_PASSE, false);
		assertEquals(Statut.INVALIDE, sansCgu.statut());
		assertEquals(Map.of("cgu", ActivationService.CGU_NON_ACCEPTEES), sansCgu.erreurs());
		assertEquals("Vous devez accepter les conditions d'utilisation", ActivationService.CGU_NON_ACCEPTEES);

		ResultatActivation faible = service.activer(JETON, "court", true);
		assertEquals(Map.of("motDePasse", ValidationInscription.MOT_DE_PASSE_FAIBLE), faible.erreurs());
		assertEquals(RegleMotDePasse.nonRespectees("court"), faible.reglesNonRespectees());
		assertTrue(faible.reglesNonRespectees().contains(RegleMotDePasse.LONGUEUR));

		ResultatActivation lesDeux = service.activer(JETON, null, false);
		assertEquals(java.util.Set.of("motDePasse", "cgu"), lesDeux.erreurs().keySet());
		assertFalse(sophie.isActif());
		assertNull(invitation.getUtiliseeLe());
	}

	// Logs : activé (info), lien refusé ou formulaire invalide (warn) ; jamais le jeton ni le mot de passe.
	@Test
	void logs() {
		try (JournalCapture journal = JournalCapture.de(EvenementMetier.class)) {
			service.verifier(JETON);
			service.activer(JETON, MOT_DE_PASSE, false);
			assertEquals("cgu", journal.seule(Level.WARN).getMDCPropertyMap().get("champsInvalides"));
			service.activer(JETON, MOT_DE_PASSE, true);
			ILoggingEvent active = journal.seule(Level.INFO);
			assertEquals("compte.activation", active.getMDCPropertyMap().get("event"));
			assertEquals("active", active.getMDCPropertyMap().get("outcome"));
			assertEquals("DIRIGEANT", active.getMDCPropertyMap().get("role"));
			service.verifier(JETON);
			service.verifier("inconnu");
			assertEquals(3, journal.lignes().stream().filter(l -> l.getLevel() == Level.WARN).count());
			for (ILoggingEvent ligne : journal.lignes()) {
				String texte = ligne.getFormattedMessage() + ligne.getMDCPropertyMap();
				assertFalse(texte.contains(JETON));
				assertFalse(texte.contains(MOT_DE_PASSE));
				assertFalse(texte.contains("sophie"));
			}
		}
	}

}
