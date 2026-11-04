package com.example.gestionfinanciere.compte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompteTest {

	@Test
	void conserveLesValeursDuCompte() {
		Compte compte = new Compte("jean.dupont@cabinet-test.fr", "empreinte", Role.COLLABORATEUR, true);
		assertNull(compte.getId());
		assertEquals("jean.dupont@cabinet-test.fr", compte.getEmail());
		assertEquals("empreinte", compte.getMotDePasseHash());
		assertEquals(Role.COLLABORATEUR, compte.getRole());
		assertTrue(compte.isActif());
	}

}
