package com.example.gestionfinanciere.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

class ClientControllerTest {

	private final ClientService clients = mock(ClientService.class);
	private final ClientController controller = new ClientController(clients);

	@Test
	void listerDelegueAuService() {
		List<Client> attendus = List.of(new Client("Sophie Martin", "Boulangerie Martin", 3));
		when(clients.lister()).thenReturn(attendus);
		assertEquals(attendus, controller.lister());
	}

}
