package com.example.gestionfinanciere.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		Compte claire = Compte.administrateurDe(cabinet, "claire@test.fr", "x", "Claire Martin");
		assertEquals(Role.COLLABORATEUR, claire.getRole());
		assertTrue(claire.isAdministrateur());
		assertEquals(cabinet, claire.getCabinet());

		Compte jean = new Compte("jean@test.fr", "x", Role.COLLABORATEUR, true, "Jean Dupont");
		assertFalse(jean.isAdministrateur());
		assertNull(jean.getCabinet());
	}

}
