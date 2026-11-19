package com.example.gestionfinanciere;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.gestionfinanciere.cabinet.CabinetRepository;
import com.example.gestionfinanciere.compte.CompteRepository;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.dossier.DossierRepository;

// US-58 Bouchon désactivé (réglage par défaut, comme en preprod/prod) et aucune connexion bancaire.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TransactionSansBouchonIT {

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

	// « Aucune transaction » : liste vide, aucune donnée de test.
	@Test
	void aucuneTransaction() throws Exception {
		String[] jeanEtDossier = TransactionIT.jeanEtSaBoulangerie(cabinets, comptes, dossiers, sessions);

		HttpResponse<String> reponse = TransactionIT.transactions(HttpClient.newHttpClient(), port, jeanEtDossier[1],
				jeanEtDossier[0]);

		assertThat(reponse.statusCode()).isEqualTo(200);
		assertThat(reponse.body()).isEqualTo("[]");
	}

}
