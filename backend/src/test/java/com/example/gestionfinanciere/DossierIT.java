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
import com.example.gestionfinanciere.dossier.Dossier;
import com.example.gestionfinanciere.dossier.DossierRepository;
import com.example.gestionfinanciere.facture.Facture;
import com.example.gestionfinanciere.facture.FactureRepository;
import org.springframework.test.util.ReflectionTestUtils;

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

	@Autowired
	private DossierRepository dossiersEnBase;

	@Autowired
	private FactureRepository factures;

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

	// US-11 : en-tête (RG1) avec l'état de l'espace client ; RG3 : autre cabinet, adresse tapée à
	// la main ou sans connexion -> aucun dossier.
	@Test
	void us11OuvrirUnDossier() throws Exception {
		String jeton = jeanConnecte();
		Long id = idDe(creer(jeton, "Garage Morel SAS", "798654321"));

		assertThat(appel("GET", "/dossiers/" + id, null, jeton).body())
				.contains("\"raisonSociale\":\"Garage Morel SAS\"", "\"siren\":\"798654321\"",
						"\"etatEspaceClient\":\"NON_INVITE\"");
		comptes.save(Compte.dirigeantInvite(id, "paul+" + id + "@test.fr", "x", "Paul"));
		assertThat(appel("GET", "/dossiers/" + id, null, jeton).body()).contains("\"etatEspaceClient\":\"INVITE\"");

		for (String chemin : new String[] { "/dossiers/" + id, "/dossiers/abc", "/dossiers/999999999" }) {
			String jetonAutre = chemin.endsWith(String.valueOf(id)) ? jeanConnecte() : jeton;
			HttpResponse<String> refus = appel("GET", chemin, null, jetonAutre);
			assertThat(refus.statusCode()).isEqualTo(404);
			assertThat(refus.body()).contains("Dossier introuvable ou accès non autorisé")
					.doesNotContain("Garage Morel SAS");
		}
		assertThat(appel("GET", "/dossiers/" + id, null, null).statusCode()).isEqualTo(401);
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

	// US-09 RG1 et RG3 : dossiers du cabinet seulement, triés, avec factures et état de l'espace client.
	@Test
	void us09ListeDesDossiersDuCabinet() throws Exception {
		String jeton = jeanConnecte();
		Long boulangerie = idDe(creer(jeton, "Boulangerie Leroy SARL", "812345678"));
		Long garage = idDe(creer(jeton, "Garage Morel SAS", "798654321"));
		Long fleurs = idDe(creer(jeton, "Fleurs Martin EURL", "812345679"));
		// Un autre cabinet a ses propres dossiers, invisibles pour Jean.
		creer(jeanConnecte(), "Autre Cabinet Client", "111222333");

		// Boulangerie : 2 factures, dirigeante active ; Garage : dirigeant invité ; Fleurs : rien.
		Dossier dossierBoulangerie = dossiersEnBase.findById(boulangerie).orElseThrow();
		Compte jean = dossierBoulangerie.getCreePar();
		factures.save(new Facture(dossierBoulangerie, Facture.Sens.ACHAT, "a.pdf", "application/pdf", Instant.now(),
				jean));
		factures.save(new Facture(dossierBoulangerie, Facture.Sens.VENTE, "v.pdf", "application/pdf", Instant.now(),
				jean));
		Compte sophie = Compte.dirigeantInvite(boulangerie, "sophie+" + boulangerie + "@test.fr", "x", "Sophie");
		ReflectionTestUtils.setField(sophie, "actif", true);
		comptes.save(sophie);
		comptes.save(Compte.dirigeantInvite(garage, "paul+" + garage + "@test.fr", "x", "Paul"));

		HttpResponse<String> liste = appel("GET", "/dossiers", null, jeton);
		assertThat(liste.statusCode()).isEqualTo(200);
		assertThat(liste.body()).isEqualTo("""
				[{"id":%d,"raisonSociale":"Boulangerie Leroy SARL","siren":"812345678","nombreFactures":2,\
				"etatEspaceClient":"ACTIF","comptables":[]},\
				{"id":%d,"raisonSociale":"Fleurs Martin EURL","siren":"812345679","nombreFactures":0,\
				"etatEspaceClient":"NON_INVITE","comptables":[]},\
				{"id":%d,"raisonSociale":"Garage Morel SAS","siren":"798654321","nombreFactures":0,\
				"etatEspaceClient":"INVITE","comptables":[]}]""".formatted(boulangerie, fleurs, garage));
	}

	// US-09 RG2 : recherche par raison sociale ou par SIREN ; CA vide.
	@Test
	void us09RechercheEtListeVide() throws Exception {
		String jeton = jeanConnecte();
		assertThat(appel("GET", "/dossiers", null, jeton).body()).isEqualTo("[]");

		creer(jeton, "Boulangerie Leroy SARL", "812345678");
		creer(jeton, "Garage Morel SAS", "798654321");

		assertThat(appel("GET", "/dossiers?recherche=leroy", null, jeton).body())
				.contains("Boulangerie Leroy SARL").doesNotContain("Garage");
		assertThat(appel("GET", "/dossiers?recherche=798%20654", null, jeton).body())
				.contains("Garage Morel SAS").doesNotContain("Boulangerie");
		assertThat(appel("GET", "/dossiers?recherche=inconnu", null, jeton).body()).isEqualTo("[]");

		// RG3 : sans connexion, aucune liste.
		assertThat(appel("GET", "/dossiers", null, null).statusCode()).isEqualTo(401);
	}

	private static Long idDe(HttpResponse<String> reponse) {
		Matcher id = ID.matcher(reponse.body());
		assertThat(id.find()).isTrue();
		return Long.valueOf(id.group(1));
	}

	@Test
	void rg1ChampsObligatoiresEtSession() throws Exception {
		HttpResponse<String> invalide = creer(jeanConnecte(), "", "123");
		assertThat(invalide.statusCode()).isEqualTo(400);
		assertThat(invalide.body()).contains("Ce champ est obligatoire", "Le SIREN comporte 9 chiffres");

		assertThat(creer(null, "Fleurs Martin EURL", "812345679").statusCode()).isEqualTo(401);
	}

}
