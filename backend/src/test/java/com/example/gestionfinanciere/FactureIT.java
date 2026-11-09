package com.example.gestionfinanciere;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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

// US-12 Factures d'un dossier : vrai serveur HTTP, migrations Flyway et PostgreSQL 16 jetable,
// en mode test (factures d'exemple, le dépôt US-15 n'existant pas encore).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "pointeo.mode-test=true")
@Testcontainers
class FactureIT {

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

	private String dossier(String jeton) throws IOException, InterruptedException {
		HttpResponse<String> creation = appel("POST", "/dossiers", """
				{"raisonSociale":"Fleurs Martin EURL","siren":"812345679"}""", jeton);
		Matcher id = ID.matcher(creation.body());
		assertThat(id.find()).isTrue();
		return "/dossiers/" + id.group(1);
	}

	// CA vide, puis RG1 (achats / ventes), RG2 (fichier, date, déposée par, « Reçue »), RG3 (« Nouvelle »).
	@Test
	void facturesDUnDossier() throws Exception {
		String jeton = jeanConnecte();
		String dossier = dossier(jeton);

		assertThat(appel("GET", dossier + "/factures?sens=ACHAT", null, jeton).body())
				.isEqualTo("{\"factures\":[],\"exemplesPossibles\":true}");

		assertThat(appel("POST", dossier + "/factures/exemples", null, jeton).statusCode()).isEqualTo(201);

		HttpResponse<String> achats = appel("GET", dossier + "/factures?sens=ACHAT", null, jeton);
		assertThat(achats.statusCode()).isEqualTo(200);
		// La plus récente d'abord : le ticket (nouveau), puis l'électricité (déjà ouverte).
		assertThat(achats.body()).containsSubsequence("\"nomFichier\":\"ticket-fournitures.png\"",
				"\"deposeePar\":\"Jean Dupont\",\"statut\":\"RECUE\",\"nouvelle\":true",
				"\"nomFichier\":\"facture-electricite-octobre.pdf\"", "\"nouvelle\":false");
		assertThat(achats.body()).doesNotContain("facture-client-0042.pdf");

		// Sans « sens » : factures fournisseurs (onglet par défaut).
		assertThat(appel("GET", dossier + "/factures", null, jeton).body()).isEqualTo(achats.body());

		HttpResponse<String> ventes = appel("GET", dossier + "/factures?sens=VENTE", null, jeton);
		assertThat(ventes.body()).contains("facture-client-0042.pdf").doesNotContain("ticket-fournitures.png");

		assertThat(appel("GET", dossier + "/factures?sens=AUTRE", null, jeton).statusCode()).isEqualTo(400);
	}

	// Droits du dossier : autre cabinet ou sans connexion -> aucune facture.
	@Test
	void controleDAcces() throws Exception {
		String jeton = jeanConnecte();
		String dossier = dossier(jeton);
		appel("POST", dossier + "/factures/exemples", null, jeton);

		String autreCabinet = jeanConnecte();
		HttpResponse<String> refus = appel("GET", dossier + "/factures?sens=ACHAT", null, autreCabinet);
		assertThat(refus.statusCode()).isEqualTo(404);
		assertThat(refus.body()).contains("Dossier introuvable ou accès non autorisé").doesNotContain("ticket");
		assertThat(appel("POST", dossier + "/factures/exemples", null, autreCabinet).statusCode()).isEqualTo(404);

		assertThat(appel("GET", dossier + "/factures", null, null).statusCode()).isEqualTo(401);
		assertThat(appel("GET", "/dossiers/abc/factures", null, jeton).statusCode()).isEqualTo(404);
	}

	private Long premiereFacture(String dossier, String sens, String jeton) throws IOException, InterruptedException {
		Matcher id = ID.matcher(appel("GET", dossier + "/factures?sens=" + sens, null, jeton).body());
		assertThat(id.find()).isTrue();
		return Long.valueOf(id.group(1));
	}

	private HttpResponse<byte[]> fichier(String chemin, String jeton) throws IOException, InterruptedException {
		HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin));
		if (jeton != null) {
			requete.header("Authorization", "Bearer " + jeton);
		}
		return http.send(requete.build(), HttpResponse.BodyHandlers.ofByteArray());
	}

	private static String debut(byte[] contenu, int taille) {
		return new String(contenu, 0, Math.min(taille, contenu.length), StandardCharsets.ISO_8859_1);
	}

	// US-13 RG1 (aperçu), RG2 (fichier d'origine) et badge « Nouvelle » retiré à l'ouverture.
	@Test
	void us13ApercuEtTelechargement() throws Exception {
		String jeton = jeanConnecte();
		String dossier = dossier(jeton);
		appel("POST", dossier + "/factures/exemples", null, jeton);
		Long ticket = premiereFacture(dossier, "ACHAT", jeton);
		String chemin = dossier + "/factures/" + ticket;

		assertThat(appel("GET", chemin, null, jeton).body()).isEqualTo(
				"{\"id\":%d,\"nomFichier\":\"ticket-fournitures.png\",\"typeFichier\":\"image/png\",\"sens\":\"ACHAT\"}"
						.formatted(ticket));

		HttpResponse<byte[]> apercu = fichier(chemin + "/fichier", jeton);
		assertThat(apercu.statusCode()).isEqualTo(200);
		assertThat(apercu.headers().firstValue("Content-Type")).hasValue("image/png");
		assertThat(apercu.headers().firstValue("Content-Disposition").orElseThrow()).startsWith("inline;");
		assertThat(debut(apercu.body(), 4)).isEqualTo("\u0089PNG");
		assertThat(appel("GET", dossier + "/factures?sens=ACHAT", null, jeton).body())
				.doesNotContain("\"nouvelle\":true");

		Long vente = premiereFacture(dossier, "VENTE", jeton);
		HttpResponse<byte[]> telechargement = fichier(dossier + "/factures/" + vente + "/fichier?telechargement=true",
				jeton);
		assertThat(telechargement.headers().firstValue("Content-Type")).hasValue("application/pdf");
		assertThat(telechargement.headers().firstValue("Content-Disposition").orElseThrow())
				.startsWith("attachment;").contains("facture-client-0042.pdf");
		assertThat(debut(telechargement.body(), 5)).isEqualTo("%PDF-");
	}

	// US-13 RG3 : sans connexion aucun fichier ; autre cabinet ou facture d'un autre dossier : refus.
	@Test
	void us13ControleDAcces() throws Exception {
		String jeton = jeanConnecte();
		String dossier = dossier(jeton);
		appel("POST", dossier + "/factures/exemples", null, jeton);
		Long ticket = premiereFacture(dossier, "ACHAT", jeton);
		String chemin = dossier + "/factures/" + ticket + "/fichier";

		HttpResponse<byte[]> nonConnecte = fichier(chemin, null);
		assertThat(nonConnecte.statusCode()).isEqualTo(401);
		assertThat(debut(nonConnecte.body(), 4)).isNotEqualTo("\u0089PNG");

		HttpResponse<byte[]> autreCabinet = fichier(chemin, jeanConnecte());
		assertThat(autreCabinet.statusCode()).isEqualTo(404);
		assertThat(new String(autreCabinet.body(), StandardCharsets.UTF_8))
				.contains("Facture introuvable ou accès non autorisé");

		// Facture du dossier de Jean demandée à travers un autre de ses dossiers.
		HttpResponse<String> autreDossier = appel("POST", "/dossiers",
				"{\"raisonSociale\":\"Garage Morel SAS\",\"siren\":\"798654321\"}", jeton);
		Matcher id = ID.matcher(autreDossier.body());
		assertThat(id.find()).isTrue();
		assertThat(fichier("/dossiers/" + id.group(1) + "/factures/" + ticket + "/fichier", jeton).statusCode())
				.isEqualTo(404);

		// Ces refus n'ont pas ouvert la facture : toujours « Nouvelle ».
		assertThat(appel("GET", dossier + "/factures?sens=ACHAT", null, jeton).body()).contains("\"nouvelle\":true");
	}

}
