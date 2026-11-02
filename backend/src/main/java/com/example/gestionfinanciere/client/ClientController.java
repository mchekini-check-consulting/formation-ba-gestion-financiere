package com.example.gestionfinanciere.client;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// GET /clients : liste des clients, exposée par le gateway sous /backend/clients.
@RestController
@RequestMapping("/clients")
public class ClientController {

	private final ClientService clients;

	public ClientController(ClientService clients) {
		this.clients = clients;
	}

	@GetMapping
	public List<Client> lister() {
		return clients.lister();
	}

}
