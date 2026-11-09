package com.example.gestionfinanciere;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Sans POINTEO_SWAGGER (preprod, prod) : le contrat des API n'est pas publié.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SwaggerDesactiveIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Value("${local.server.port}")
	private int port;

	@Test
	void neDonneNiLeContratNiLaPage() throws IOException, InterruptedException {
		HttpClient http = HttpClient.newHttpClient();
		for (String chemin : new String[] {"/v3/api-docs", "/swagger-ui/index.html"}) {
			HttpResponse<String> reponse = http.send(
					HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin)).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			assertThat(reponse.statusCode()).as(chemin).isNotEqualTo(200);
		}
	}
}
