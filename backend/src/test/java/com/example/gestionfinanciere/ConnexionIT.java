package com.example.gestionfinanciere;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.gestionfinanciere.compte.CompteRepository;

// US-03 Se connecter, côté backend : vrai serveur HTTP, migrations Flyway
// (comptes de démonstration V6) et PostgreSQL 16 jetable. make test-integration ; Docker requis.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ConnexionIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	// Mot de passe des comptes de démonstration (V6__compte_donnees_demo.sql).
	private static final String MOT_DE_PASSE_DEMO = "Pointeo2026!";

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private CompteRepository comptes;

	private final HttpClient http = HttpClient.newHttpClient();

	private HttpResponse<String> seConnecter(String email, String motDePasse)
			throws IOException, InterruptedException {
		String corps = "{\"email\":\"" + email + "\",\"motDePasse\":\"" + motDePasse + "\"}";
		HttpRequest requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/auth/connexion"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(corps))
				.build();
		return http.send(requete, HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void comptesDeDemonstrationCharges() {
		assertThat(comptes.findByEmail("jean.dupont@cabinet-test.fr")).isPresent();
		assertThat(comptes.findByEmail("sophie.martin@boulangerie-test.fr")).isPresent();
	}

	@Test
	void ca1CollaborateurBonMotDePassePasseAuCodeSms() throws Exception {
		HttpResponse<String> reponse = seConnecter("jean.dupont@cabinet-test.fr", MOT_DE_PASSE_DEMO);
		assertThat(reponse.statusCode()).isEqualTo(200);
		assertThat(reponse.body()).contains("\"role\":\"COLLABORATEUR\"").contains("\"etapeSuivante\":\"CODE_SMS\"");
	}

	@Test
	void dirigeantBonMotDePasse() throws Exception {
		HttpResponse<String> reponse = seConnecter("sophie.martin@boulangerie-test.fr", MOT_DE_PASSE_DEMO);
		assertThat(reponse.statusCode()).isEqualTo(200);
		assertThat(reponse.body()).contains("\"role\":\"DIRIGEANT\"");
	}

	@Test
	void rg1EmailEnMajusculesAccepte() throws Exception {
		assertThat(seConnecter("Jean.Dupont@Cabinet-Test.fr", MOT_DE_PASSE_DEMO).statusCode()).isEqualTo(200);
	}

	@Test
	void ca2MauvaisMotDePasse() throws Exception {
		HttpResponse<String> reponse = seConnecter("jean.dupont@cabinet-test.fr", "mauvais");
		assertThat(reponse.statusCode()).isEqualTo(401);
		assertThat(reponse.body()).contains("Email ou mot de passe incorrect");
	}

	@Test
	void rg2EmailInconnuMemeMessage() throws Exception {
		HttpResponse<String> reponse = seConnecter("inconnu@cabinet-test.fr", MOT_DE_PASSE_DEMO);
		assertThat(reponse.statusCode()).isEqualTo(401);
		assertThat(reponse.body()).isEqualTo(seConnecter("jean.dupont@cabinet-test.fr", "mauvais").body());
	}

}
