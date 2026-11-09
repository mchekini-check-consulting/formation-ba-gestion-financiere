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

// Contrat d'interface des API (Swagger / OpenAPI), activé comme en local, dev et qualif.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "POINTEO_SWAGGER=true")
@Testcontainers
class SwaggerIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Value("${local.server.port}")
	private int port;

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	void publieLeContratDesApi() throws IOException, InterruptedException {
		HttpResponse<String> reponse = get("/v3/api-docs", null);

		assertThat(reponse.statusCode()).isEqualTo(200);
		assertThat(reponse.body()).contains("\"openapi\"", "/auth/connexion", "/auth/activation", "/dossiers");
	}

	@Test
	void afficheLaPageSwagger() throws IOException, InterruptedException {
		assertThat(get("/swagger-ui/index.html", null).statusCode()).isEqualTo(200);
	}

	@Test
	void derriereLeGatewayLaPageRetrouveLeContratSousBackend() throws IOException, InterruptedException {
		HttpResponse<String> reponse = get("/v3/api-docs/swagger-config", "/backend");

		assertThat(reponse.statusCode()).isEqualTo(200);
		assertThat(reponse.body()).contains("/backend/v3/api-docs");
	}

	private HttpResponse<String> get(String chemin, String prefixeGateway) throws IOException, InterruptedException {
		HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin)).GET();
		if (prefixeGateway != null) {
			requete.header("X-Forwarded-Prefix", prefixeGateway);
		}
		return http.send(requete.build(), HttpResponse.BodyHandlers.ofString());
	}
}
