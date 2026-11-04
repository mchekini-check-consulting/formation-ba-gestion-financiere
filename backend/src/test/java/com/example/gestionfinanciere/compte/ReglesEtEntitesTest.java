package com.example.gestionfinanciere.compte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.gestionfinanciere.notification.EnvoiEmailJournal;
import com.example.gestionfinanciere.notification.EnvoiImpossibleException;
import com.example.gestionfinanciere.notification.EnvoiSmsJournal;

class ReglesEtEntitesTest {

	private static final Instant T0 = Instant.parse("2026-11-04T09:00:00Z");

	private final Compte jean = new Compte("jean.dupont@cabinet-test.fr", "empreinte", Role.COLLABORATEUR, true,
			"Jean Dupont", "06 12 34 56 42");

	@Test
	void reglesDuMotDePasse() {
		assertEquals(List.of(), RegleMotDePasse.nonRespectees("Nouveau#Pointeo2026"));
		assertEquals(List.of(RegleMotDePasse.LONGUEUR, RegleMotDePasse.MAJUSCULE, RegleMotDePasse.MINUSCULE,
				RegleMotDePasse.CHIFFRE, RegleMotDePasse.SPECIAL), RegleMotDePasse.nonRespectees(null));
		assertEquals(List.of(RegleMotDePasse.SPECIAL), RegleMotDePasse.nonRespectees("SansSpecial2026"));
		assertEquals(List.of(RegleMotDePasse.LONGUEUR_MAX), RegleMotDePasse.nonRespectees("Aa1#" + "x".repeat(70)));
	}

	@Test
	void jetonsAleatoiresEtEmpreintes() {
		String jeton = Jetons.nouveau();
		assertEquals(43, jeton.length());
		assertNotEquals(jeton, Jetons.nouveau());
		assertEquals(64, Jetons.empreinte(jeton).length());
		assertEquals(Jetons.empreinte(jeton), Jetons.empreinte(jeton));
		assertTrue(Jetons.codeSixChiffres().matches("\\d{6}"));
	}

	@Test
	void compte() {
		assertNull(jean.getId());
		assertEquals("jean.dupont@cabinet-test.fr", jean.getEmail());
		assertEquals("empreinte", jean.getMotDePasseHash());
		assertEquals(Role.COLLABORATEUR, jean.getRole());
		assertTrue(jean.isActif());
		assertEquals("Jean Dupont", jean.getNomAffiche());
		assertEquals("06 12 34 56 42", jean.getTelephone());
		assertEquals("06 •• •• •• 42", jean.telephoneMasque());
		assertEquals("•• •• •• •• ••",
				new Compte("a@b.fr", "e", Role.DIRIGEANT, true, "A", "").telephoneMasque());
	}

	@Test
	void compteBloqueEtDebloque() {
		assertFalse(jean.enregistrerEchec(T0, 2, Duration.ofMinutes(15)));
		assertEquals(1, jean.getEchecsConsecutifs());
		assertTrue(jean.enregistrerEchec(T0, 2, Duration.ofMinutes(15)));
		assertTrue(jean.estBloque(T0.plusSeconds(60)));
		assertFalse(jean.estBloque(T0.plus(Duration.ofMinutes(15))));
		jean.changerMotDePasse("nouvelle");
		assertEquals("nouvelle", jean.getMotDePasseHash());
		assertFalse(jean.estBloque(T0));
	}

	@Test
	void codeSms() {
		CodeSms code = new CodeSms(jean, "defi", "code", T0, T0.plusSeconds(300));
		assertNull(code.getId());
		assertEquals(jean, code.getCompte());
		assertEquals("defi", code.getEmpreinteDefi());
		assertFalse(code.estExpire(T0.plusSeconds(299)));
		assertTrue(code.estExpire(T0.plusSeconds(300)));
		assertEquals(1, code.enregistrerCodeFaux());
		assertEquals(1, code.getCodesFaux());
		code.remplacerCode("nouveau", T0.plusSeconds(60), T0.plusSeconds(360));
		assertEquals("nouveau", code.getEmpreinteCode());
		assertEquals(T0.plusSeconds(60), code.getEnvoyeLe());
		assertFalse(code.isTermine());
		code.terminer();
		assertTrue(code.isTermine());
	}

	@Test
	void sessionEtJetonDeReinitialisation() {
		SessionUtilisateur session = new SessionUtilisateur(jean, "empreinte", T0);
		assertNull(session.getId());
		assertEquals(jean, session.getCompte());
		session.enregistrerActivite(T0.plusSeconds(10));
		assertEquals(T0.plusSeconds(10), session.getDerniereActivite());

		JetonReinitialisation lien = new JetonReinitialisation(jean, "empreinte", T0.plusSeconds(3600));
		assertNull(lien.getId());
		assertEquals(jean, lien.getCompte());
		assertEquals("empreinte", lien.getEmpreinteJeton());
		assertEquals(T0.plusSeconds(3600), lien.getExpireLe());
		assertTrue(lien.estValable(T0));
		lien.utiliser(T0);
		assertEquals(T0, lien.getUtiliseLe());
		assertFalse(lien.estValable(T0));
	}

	@Test
	void envoisJournalisesSansErreur() {
		new EnvoiEmailJournal().envoyer("jean@test.fr", "sujet", "corps");
		new EnvoiSmsJournal().envoyer("0612345642", "message");
		assertEquals("opérateur", new EnvoiImpossibleException("opérateur", null).getMessage());
	}

	@Test
	void configuration() {
		AuthConfig config = new AuthConfig();
		assertTrue(config.encodeurMotDePasse().matches("a", config.encodeurMotDePasse().encode("a")));
		assertEquals(java.time.ZoneOffset.UTC, config.horloge().getZone());
		HorlogeReglable horloge = new HorlogeReglable(T0);
		assertEquals(horloge, horloge.withZone(java.time.ZoneId.of("Europe/Paris")));
	}

}
