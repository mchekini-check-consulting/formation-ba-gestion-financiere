package com.example.gestionfinanciere.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class ClientServiceTest {

	private final ClientRepository clients = mock(ClientRepository.class);
	private final ClientService service = new ClientService(clients);

	@Test
	void listerRenvoieLesClientsParId() {
		List<Client> attendus = List.of(new Client("Sophie Martin", "Boulangerie Martin", 3));
		when(clients.findAll(Sort.by("id"))).thenReturn(attendus);
		assertEquals(attendus, service.lister());
	}

}
