package com.example.gestionfinanciere;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

// US-10 Créer un dossier client (PTO-17) : vrai serveur HTTP, migrations Flyway (dont V10) et
// PostgreSQL 16 jetable. Chaque test crée son propre cabinet.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DossierIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private CabinetRepository cabinets;

	@Autowired
	private CompteRepository comptes;

	@Autowired
	private SessionService sessions;

	private final HttpClient http = HttpClient.newHttpClient();

	// Jean, collaborateur d'un nouveau cabinet, connecté : renvoie son jeton de session.
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

	private HttpResponse<String> creer(String jeton, String raisonSociale, String siren)
			throws IOException, InterruptedException {
		return appel("POST", "/dossiers", """
				{"raisonSociale":"%s","siren":"%s"}""".formatted(raisonSociale, siren), jeton);
	}

	@Test
	void ca1DossierCreePuisOuvert() throws Exception {
		String jeton = jeanConnecte();

		HttpResponse<String> creation = creer(jeton, "Fleurs Martin EURL", "812 345 679");
		assertThat(creation.statusCode()).isEqualTo(201);
		assertThat(creation.body()).contains("\"raisonSociale\":\"Fleurs Martin EURL\"", "\"siren\":\"812345679\"");
		// Logs : identifiant de la requête, à retrouver dans Grafana.
		assertThat(creation.headers().firstValue("X-Request-Id")).isPresent();

		Matcher id = ID.matcher(creation.body());
		assertThat(id.find()).isTrue();
		HttpResponse<String> ouvert = appel("GET", "/dossiers/" + id.group(1), null, jeton);
		assertThat(ouvert.statusCode()).isEqualTo(200);
		assertThat(ouvert.body()).contains("Fleurs Martin EURL");

		// Un autre cabinet ne voit pas ce dossier.
		assertThat(appel("GET", "/dossiers/" + id.group(1), null, jeanConnecte()).statusCode()).isEqualTo(404);
	}

	@Test
	void ca2DoublonDansLeCabinetMaisPasDansUnAutre() throws Exception {
		String jeton = jeanConnecte();
		assertThat(creer(jeton, "Boulangerie Leroy SARL", "812345678").statusCode()).isEqualTo(201);

		HttpResponse<String> doublon = creer(jeton, "Boulangerie Leroy", "812 345 678");
		assertThat(doublon.statusCode()).isEqualTo(409);
		assertThat(doublon.body()).contains("Un dossier existe déjà pour ce SIREN dans ce cabinet");

		// RG2 : deux cabinets différents peuvent suivre la même entreprise.
		assertThat(creer(jeanConnecte(), "Boulangerie Leroy SARL", "812345678").statusCode()).isEqualTo(201);
	}

	@Test
	void rg1ChampsObligatoiresEtSession() throws Exception {
		HttpResponse<String> invalide = creer(jeanConnecte(), "", "123");
		assertThat(invalide.statusCode()).isEqualTo(400);
		assertThat(invalide.body()).contains("Ce champ est obligatoire", "Le SIREN comporte 9 chiffres");

		assertThat(creer(null, "Fleurs Martin EURL", "812345679").statusCode()).isEqualTo(401);
	}

}
