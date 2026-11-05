package com.example.gestionfinanciere.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.Role;

class CabinetEtInscriptionTest {

	private static final Instant T0 = Instant.parse("2026-11-05T09:00:00Z");

	@Test
	void cabinet() {
		Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, "TEST-4821", T0, T0.plusSeconds(60));
		assertNull(cabinet.getId());
		assertEquals("Cabinet Test", cabinet.getNom());
		assertEquals("123456789", cabinet.getSiren());
		assertEquals(120, cabinet.getNombreDossiersPrevus());
		assertEquals("TEST-4821", cabinet.getCodeParrainageSaisi());
		assertEquals(T0, cabinet.getCguAccepteesLe());
		assertEquals(T0.plusSeconds(60), cabinet.getCreeLe());
	}

	@Test
	void administrateurDuCabinet() {
		Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, T0, T0);
		Compte claire = Compte.administrateurDe(cabinet, "claire@test.fr", "x", "Claire Martin", "0612345678");
		assertEquals(Role.COLLABORATEUR, claire.getRole());
		assertTrue(claire.isAdministrateur());
		assertEquals(cabinet, claire.getCabinet());
		assertEquals("06 •• •• •• 78", claire.telephoneMasque());

		Compte jean = new Compte("jean@test.fr", "x", Role.COLLABORATEUR, true, "Jean Dupont", "0612345642");
		assertFalse(jean.isAdministrateur());
		assertNull(jean.getCabinet());
	}

	@Test
	void inscription() {
		FormulaireValide f = new FormulaireValide("Cabinet Test", "123456789", "Claire", "Martin", "claire@test.fr",
				"0612345678", "secret", 120, null);
		Inscription i = new Inscription(f, "hash", T0, "defi", "code", T0, T0.plus(Duration.ofMinutes(5)));
		assertNull(i.getId());
		assertEquals("Cabinet Test", i.getNomCabinet());
		assertEquals("Claire", i.getPrenom());
		assertEquals("Martin", i.getNom());
		assertEquals("Claire Martin", i.nomAffiche());
		assertEquals("0612345678", i.getTelephone());
		assertEquals("hash", i.getMotDePasseHash());
		assertNull(i.getCodeParrainageSaisi());
		assertEquals(T0, i.getEnvoyeLe());
		assertFalse(i.estExpiree(T0.plus(Duration.ofMinutes(4))));
		assertTrue(i.estExpiree(T0.plus(Duration.ofMinutes(5))));
		assertEquals(0, i.getCodesFaux());
		assertEquals(1, i.enregistrerCodeFaux());
		assertFalse(i.isTerminee());
		i.terminer();
		assertTrue(i.isTerminee());
		i.remplacerCode("nouveau", T0.plusSeconds(60), T0.plusSeconds(360));
		assertEquals("nouveau", i.getEmpreinteCode());
		assertEquals(T0.plusSeconds(360), i.getExpireLe());
	}

}
