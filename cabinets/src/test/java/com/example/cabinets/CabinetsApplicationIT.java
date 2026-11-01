package com.example.cabinets;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.cabinets.cabinet.Cabinet;
import com.example.cabinets.cabinet.CabinetRepository;

// Démarrage complet, connecté à un PostgreSQL 16 jetable (migrations Flyway comprises).
// make test-integration ; Docker requis.
@SpringBootTest
@Testcontainers
class CabinetsApplicationIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private CabinetRepository cabinets;

	@Test
	void contextLoads() {
	}

	@Test
	void cabinetEnregistreEtRelu() {
		Cabinet cree = cabinets.save(new Cabinet("Cabinet Martin", "812345678", "contact@martin.fr", 120));
		assertThat(cree.getId()).isNotNull();
		assertThat(cabinets.existsBySiren("812345678")).isTrue();
		assertThat(cabinets.existsBySirenAndIdNot("812345678", cree.getId())).isFalse();
		assertThat(cabinets.findById(cree.getId())).hasValueSatisfying(cabinet -> {
			assertThat(cabinet.getNom()).isEqualTo("Cabinet Martin");
			assertThat(cabinet.getNombreDossiers()).isEqualTo(120);
			assertThat(cabinet.getCreeLe()).isNotNull();
		});
	}

}
