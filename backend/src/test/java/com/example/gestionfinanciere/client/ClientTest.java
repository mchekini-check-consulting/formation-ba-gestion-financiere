package com.example.gestionfinanciere.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ClientTest {

	@Test
	void conserveLesValeursDuClient() {
		Client client = new Client("Sophie Martin", "Boulangerie Martin", 3);
		assertNull(client.getId());
		assertEquals("Sophie Martin", client.getNomClient());
		assertEquals("Boulangerie Martin", client.getNomEntreprise());
		assertEquals(3, client.getNombreDonneesConnectees());
	}

}
