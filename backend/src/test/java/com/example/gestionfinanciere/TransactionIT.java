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
import com.example.gestionfinanciere.dossier.Dossier;
import com.example.gestionfinanciere.dossier.DossierRepository;

// US-58 Bouchon bancaire activé : vrai serveur HTTP et PostgreSQL 16 jetable.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "pointeo.bouchon-bancaire=true")
@Testcontainers
class TransactionIT {

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
	private DossierRepository dossiers;

	@Autowired
	private SessionService sessions;

	private final HttpClient http = HttpClient.newHttpClient();

	// Jean, administrateur d'un nouveau cabinet, avec le dossier Boulangerie Dupont ; renvoie le jeton et le dossier.
	static String[] jeanEtSaBoulangerie(CabinetRepository cabinets, CompteRepository comptes,
			DossierRepository dossiers, SessionService sessions) {
		String id = UUID.randomUUID().toString().substring(0, 8);
		String siren = String.valueOf(100_000_000 + Math.abs(id.hashCode() % 900_000_000));
		Cabinet cabinet = cabinets.save(new Cabinet("Cabinet Test", siren, 120, null, Instant.now(), Instant.now()));
		Compte jean = comptes.save(Compte.administrateurDe(cabinet, "jean+" + id + "@cabinet-test.fr", "x",
				"Jean Dupont"));
		Dossier boulangerie = dossiers.save(new Dossier(cabinet, "Boulangerie Dupont", siren, jean, Instant.now()));
		return new String[] { sessions.ouvrir(jean), String.valueOf(boulangerie.getId()) };
	}

	static HttpResponse<String> transactions(HttpClient http, int port, String dossier, String jeton)
			throws IOException, InterruptedException {
		HttpRequest.Builder requete = HttpRequest
				.newBuilder(URI.create("http://localhost:" + port + "/dossiers/" + dossier + "/transactions")).GET();
		if (jeton != null) {
			requete.header("Authorization", "Bearer " + jeton);
		}
		return http.send(requete.build(), HttpResponse.BodyHandlers.ofString());
	}

	// Bouchon activé : le dossier Boulangerie Dupont reçoit le jeu de test, marqué « Bouchon ».
	@Test
	void bouchonActive() throws Exception {
		String[] jeanEtDossier = jeanEtSaBoulangerie(cabinets, comptes, dossiers, sessions);

		HttpResponse<String> reponse = transactions(http, port, jeanEtDossier[1], jeanEtDossier[0]);

		assertThat(reponse.statusCode()).isEqualTo(200);
		assertThat(reponse.body()).contains(
				"\"libelle\":\"PRLV SEPA EDF - Facture electricite octobre\",\"tiers\":\"EDF\",\"montant\":186.40,"
						+ "\"sens\":\"SORTIE\",\"source\":\"BOUCHON\"")
				.contains("\"sens\":\"ENTREE\"").doesNotContain("\"source\":\"BANQUE\"");

		// Dossier d'un autre cabinet -> 404 ; sans connexion -> 401.
		String[] autre = jeanEtSaBoulangerie(cabinets, comptes, dossiers, sessions);
		assertThat(transactions(http, port, autre[1], jeanEtDossier[0]).statusCode()).isEqualTo(404);
		assertThat(transactions(http, port, jeanEtDossier[1], null).statusCode()).isEqualTo(401);
	}

}
