package com.example.cabinets.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CabinetTest {

	@Test
	void dateDeCreationFixeeALEnregistrement() {
		Cabinet cabinet = new Cabinet("Cabinet Martin", "812345678", "a@b.fr", 3);
		assertNull(cabinet.getId());
		assertNull(cabinet.getCreeLe());
		cabinet.avantCreation();
		assertNotNull(cabinet.getCreeLe());
		assertEquals("a@b.fr", cabinet.getEmail());
		assertEquals(3, cabinet.getNombreDossiers());
	}

}
