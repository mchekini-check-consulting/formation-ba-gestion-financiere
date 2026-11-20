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

// US-41 Plan comptable d'un dossier : vrai serveur HTTP et PostgreSQL 16 jetable (migration V20).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PlanComptableIT {

	private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

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

	private HttpResponse<String> appeler(String methode, String chemin, String corps, String jeton)
			throws IOException, InterruptedException {
		HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
				.header("Content-Type", "application/json").method(methode,
						corps == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(corps));
		if (jeton != null) {
			requete.header("Authorization", "Bearer " + jeton);
		}
		return http.send(requete.build(), HttpResponse.BodyHandlers.ofString());
	}

	// Lucie, administratrice d'un nouveau cabinet ; renvoie son jeton de session.
	private String lucie() {
		String id = UUID.randomUUID().toString().substring(0, 8);
		String siren = String.valueOf(100_000_000 + Math.abs(id.hashCode() % 900_000_000));
		Cabinet cabinet = cabinets.save(new Cabinet("Cabinet Test", siren, 120, null, Instant.now(), Instant.now()));
		Compte lucie = comptes.save(Compte.administrateurDe(cabinet, "lucie+" + id + "@cabinet-test.fr", "x",
				"Lucie Martin"));
		return sessions.ouvrir(lucie);
	}

	@Test
	void planComptableDuDossier() throws Exception {
		String jeton = lucie();
		// RG1 : le dossier créé démarre avec le plan comptable général.
		HttpResponse<String> cree = appeler("POST", "/dossiers",
				"{\"raisonSociale\":\"Boulangerie Leroy SARL\",\"siren\":\"812345679\"}", jeton);
		assertThat(cree.statusCode()).isEqualTo(201);
		Matcher id = ID.matcher(cree.body());
		assertThat(id.find()).isTrue();
		String plan = "/dossiers/" + id.group(1) + "/plan-comptable";

		HttpResponse<String> lu = appeler("GET", plan, null, jeton);
		assertThat(lu.statusCode()).isEqualTo(200);
		assertThat(lu.body()).contains("{\"numero\":\"401\",\"libelle\":\"Fournisseurs\",\"sousCompte\":false,"
				+ "\"actif\":true}")
				// RG2 : comptes par défaut.
				.contains("{\"code\":\"ACHAT\",\"libelle\":\"Achat\",\"numero\":\"607\"}")
				.contains("{\"code\":\"FRAIS_BANCAIRES\",\"libelle\":\"Frais bancaires\",\"numero\":\"627\"}")
				.contains("{\"code\":\"BANQUE\",\"libelle\":\"Banque\",\"numero\":\"512\"}");

		// CA1 : 401MOULINS « Moulins Bertrand ».
		HttpResponse<String> moulins = appeler("POST", plan,
				"{\"numero\":\"401MOULINS\",\"libelle\":\"Moulins Bertrand\"}", jeton);
		assertThat(moulins.statusCode()).isEqualTo(201);
		assertThat(appeler("GET", plan, null, jeton).body()).contains("\"numero\":\"401MOULINS\"");
		assertThat(appeler("POST", plan, "{\"numero\":\"401MOULINS\",\"libelle\":\"Doublon\"}", jeton).statusCode())
				.isEqualTo(409);

		// CA2 : 9001 -> « Numéro de compte invalide ».
		HttpResponse<String> invalide = appeler("POST", plan, "{\"numero\":\"9001\",\"libelle\":\"Test\"}", jeton);
		assertThat(invalide.statusCode()).isEqualTo(400);
		assertThat(invalide.body()).contains("Numéro de compte invalide");

		// RG3 : un compte du plan général se désactive mais ne se supprime pas.
		assertThat(appeler("DELETE", plan + "/401", null, jeton).statusCode()).isEqualTo(409);
		HttpResponse<String> desactive = appeler("PATCH", plan + "/512", "{\"actif\":false}", jeton);
		assertThat(desactive.statusCode()).isEqualTo(200);
		assertThat(desactive.body()).contains("\"actif\":false");
		assertThat(appeler("DELETE", plan + "/401MOULINS", null, jeton).statusCode()).isEqualTo(204);

		// Dossier d'un autre cabinet -> 404 ; sans connexion -> 401.
		assertThat(appeler("GET", plan, null, lucie()).statusCode()).isEqualTo(404);
		assertThat(appeler("GET", plan, null, null).statusCode()).isEqualTo(401);
	}

}
