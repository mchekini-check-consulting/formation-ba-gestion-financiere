package com.example.gestionfinanciere;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.gestionfinanciere.cabinet.CabinetRepository;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.CompteRepository;
import com.example.gestionfinanciere.notification.EmailsRecus;
import com.example.gestionfinanciere.notification.EnvoiEmail;

// US-36 Créer le compte de son cabinet (PTO-61) : vrai serveur HTTP, migrations Flyway et
// PostgreSQL 16 jetable ; le code de confirmation est lu dans l'email envoyé (simulé).
// Chaque test utilise son propre SIREN et son propre email : aucun test ne dépend d'un autre.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class InscriptionIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	private static final String MOT_DE_PASSE = "Pointeo2026!!";

	// Emails simulés : le code ou le lien est lu dans l'email envoyé, jamais dans la réponse.
	@MockitoBean
	private EnvoiEmail envoiEmail;

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private CompteRepository comptes;

	@Autowired
	private CabinetRepository cabinets;

	private final HttpClient http = HttpClient.newHttpClient();

	private HttpResponse<String> post(String chemin, String json) throws IOException, InterruptedException {
		HttpRequest requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();
		return http.send(requete, HttpResponse.BodyHandlers.ofString());
	}

	private static String champ(HttpResponse<String> reponse, String nom) {
		Matcher m = Pattern.compile("\"" + nom + "\":\"([^\"]*)\"").matcher(reponse.body());
		assertThat(m.find()).as("champ %s dans %s", nom, reponse.body()).isTrue();
		return m.group(1);
	}

	private static String nouveauSiren() {
		return String.valueOf(ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000));
	}

	private HttpResponse<String> formulaire(String siren, String email) throws IOException, InterruptedException {
		return post("/inscription", """
				{"nomCabinet":"Cabinet Test","siren":"%s","prenom":"Claire","nom":"Martin","email":"%s",
				 "motDePasse":"%s","nombreDossiers":120,"codeParrainage":"",
				 "cguAcceptees":true}""".formatted(siren, email, MOT_DE_PASSE));
	}

	private HttpResponse<String> code(String defi, String code) throws IOException, InterruptedException {
		return post("/inscription/code", "{\"defi\":\"" + defi + "\",\"code\":\"" + code + "\"}");
	}

	// Étapes 1 et 2 : renvoie la réponse du code reçu par email.
	private HttpResponse<String> creerCabinet(String siren, String email) throws IOException, InterruptedException {
		HttpResponse<String> etape1 = formulaire(siren, email);
		assertThat(etape1.statusCode()).isEqualTo(200);
		// L'email est enregistré en minuscules (US-03 RG1).
		String enregistre = email.toLowerCase(java.util.Locale.ROOT);
		assertThat(champ(etape1, "emailMasque"))
				.isEqualTo(enregistre.charAt(0) + "•••••" + enregistre.substring(enregistre.indexOf('@')));
		return code(champ(etape1, "defi"), EmailsRecus.dernierCode(envoiEmail));
	}

	@Test
	void ca1ClaireCreeCabinetTestEtEnDevientAdministratrice() throws Exception {
		String siren = nouveauSiren();
		String email = "claire.martin+" + siren + "@cabinet-test.fr";
		HttpResponse<String> etape2 = creerCabinet(siren.substring(0, 3) + " " + siren.substring(3, 6) + " "
				+ siren.substring(6), email.toUpperCase());

		assertThat(etape2.statusCode()).isEqualTo(200);
		assertThat(champ(etape2, "nomCabinet")).isEqualTo("Cabinet Test");
		assertThat(champ(etape2, "nomAffiche")).isEqualTo("Claire Martin");
		assertThat(champ(etape2, "role")).isEqualTo("COLLABORATEUR");
		assertThat(cabinets.existsBySiren(siren)).isTrue();
		Compte claire = comptes.findByEmail(email).orElseThrow();
		assertThat(claire.isAdministrateur()).isTrue();

		// Étape 3 « Cabinet prêt » : la session ouverte est active (US-07).
		HttpRequest session = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/auth/session"))
				.header("Authorization", "Bearer " + champ(etape2, "jeton")).GET().build();
		assertThat(http.send(session, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);

		// Claire peut ensuite se connecter avec son email et son mot de passe (US-03).
		HttpResponse<String> connexion = post("/auth/connexion",
				"{\"email\":\"" + email + "\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}");
		assertThat(connexion.statusCode()).isEqualTo(200);
	}

	@Test
	void ca2SirenDejaUtiliseParUnCabinet() throws Exception {
		String siren = nouveauSiren();
		assertThat(creerCabinet(siren, "premier+" + siren + "@test.fr").statusCode()).isEqualTo(200);

		HttpResponse<String> autre = formulaire(siren, "autre+" + siren + "@test.fr");
		assertThat(autre.statusCode()).isEqualTo(409);
		assertThat(autre.body()).contains(
				"Un cabinet existe déjà avec ce SIREN. Demandez à son administrateur de vous inviter.");
	}

	@Test
	void uneInscriptionAbandonneeNeBloquePasLeSiren() throws Exception {
		String siren = nouveauSiren();
		assertThat(formulaire(siren, "abandon+" + siren + "@test.fr").statusCode()).isEqualTo(200);
		assertThat(creerCabinet(siren, "reprise+" + siren + "@test.fr").statusCode()).isEqualTo(200);
	}

	@Test
	void emailDejaUtilise() throws Exception {
		HttpResponse<String> reponse = formulaire(nouveauSiren(), "Jean.Dupont@Cabinet-Test.fr");
		assertThat(reponse.statusCode()).isEqualTo(409);
		assertThat(reponse.body()).contains("Un compte existe déjà avec cet email.");
	}

	@Test
	void formulaireIncompletRefuseChampParChamp() throws Exception {
		HttpResponse<String> reponse = post("/inscription", "{\"nomCabinet\":\"Cabinet Test\",\"siren\":\"12\"}");
		assertThat(reponse.statusCode()).isEqualTo(400);
		assertThat(champ(reponse, "siren")).isEqualTo("Le SIREN comporte 9 chiffres");
		assertThat(champ(reponse, "cguAcceptees")).isEqualTo("Vous devez accepter les CGU pour créer votre compte");
	}

	@Test
	void codeFauxNeCreeRien() throws Exception {
		String siren = nouveauSiren();
		HttpResponse<String> etape1 = formulaire(siren, "faux+" + siren + "@test.fr");
		String defi = champ(etape1, "defi");
		String faux = EmailsRecus.dernierCode(envoiEmail).equals("000000") ? "111111" : "000000";

		HttpResponse<String> reponse = code(defi, faux);
		assertThat(reponse.statusCode()).isEqualTo(401);
		assertThat(reponse.body()).contains("Code incorrect. Il vous reste 2 tentatives.");
		assertThat(cabinets.existsBySiren(siren)).isFalse();

		HttpResponse<String> tropTot = post("/inscription/code/renvoyer", "{\"defi\":\"" + defi + "\"}");
		assertThat(tropTot.statusCode()).isEqualTo(429);
	}

}
