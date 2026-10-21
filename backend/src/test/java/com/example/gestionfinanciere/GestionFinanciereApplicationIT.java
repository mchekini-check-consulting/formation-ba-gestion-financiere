package com.example.gestionfinanciere;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Démarrage complet, connecté à un PostgreSQL 16 jetable (migrations Flyway comprises).
// make test-integration ; Docker requis.
@SpringBootTest
@Testcontainers
class GestionFinanciereApplicationIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Test
	void contextLoads() {
	}

}
