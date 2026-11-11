package com.example.gestionfinanciere;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.cabinet.CabinetRepository;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.CompteRepository;
import com.example.gestionfinanciere.compte.SessionService;

// US-18 Modules à venir du menu de gauche : vrai serveur HTTP, migrations V14 et V16 et PostgreSQL 16 jetable.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ModuleIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private CabinetRepository cabinets;

	@Autowired
	private CompteRepository comptes;

	@Autowired
	private SessionService sessions;

	private final HttpClient http = HttpClient.newHttpClient();

	private String jeanConnecte() {
		String id = UUID.randomUUID().toString().substring(0, 8);
		Cabinet cabinet = cabinets.save(new Cabinet("Cabinet Test", String.valueOf(100_000_000
				+ Math.abs(id.hashCode() % 900_000_000)), 120, null, Instant.now(), Instant.now()));
		Compte jean = comptes.save(Compte.administrateurDe(cabinet, "jean+" + id + "@cabinet-test.fr", "x",
				"Jean Dupont"));
		return sessions.ouvrir(jean);
	}

	private HttpResponse<String> appel(String methode, String chemin, String json, String jeton)
			throws IOException, InterruptedException {
		HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
				.header("Content-Type", "application/json")
				.method(methode, json == null ? HttpRequest.BodyPublishers.noBody()
						: HttpRequest.BodyPublishers.ofString(json));
		if (jeton != null) {
			requete.header("Authorization", "Bearer " + jeton);
		}
		return http.send(requete.build(), HttpResponse.BodyHandlers.ofString());
	}

	// CA1 : menu de gauche -> les 4 modules de la page d'accueil, dans l'ordre, « Bientôt disponible ».
	@Test
	void ca1ModulesDuMenu() throws Exception {
		HttpResponse<String> modules = appel("GET", "/modules", null, jeanConnecte());
		assertThat(modules.statusCode()).isEqualTo(200);
		assertThat(modules.body()).containsSubsequence(
				"\"code\":\"SYNCHRONISATION_BANCAIRE\",\"nom\":\"Synchronisation bancaire\"",
				"\"code\":\"RAPPROCHEMENT_AUTOMATIQUE\",\"nom\":\"Rapprochement automatique\"",
				"\"code\":\"CRM\",\"nom\":\"CRM\"",
				"\"code\":\"SUIVI_TEMPS\",\"nom\":\"Suivi des temps\"",
				"\"statut\":\"BIENTOT_DISPONIBLE\"");

		// Sans connexion -> 401.
		assertThat(appel("GET", "/modules", null, null).statusCode()).isEqualTo(401);
	}

}
