package com.example.gestionfinanciere.client;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.example.gestionfinanciere.logging.Loggable;

@Service
@Loggable
public class ClientService {

	private final ClientRepository clients;

	public ClientService(ClientRepository clients) {
		this.clients = clients;
	}

	public List<Client> lister() {
		return clients.findAll(Sort.by("id"));
	}

}
