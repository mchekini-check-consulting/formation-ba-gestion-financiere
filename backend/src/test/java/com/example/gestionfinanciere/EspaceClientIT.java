package com.example.gestionfinanciere;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
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
import com.example.gestionfinanciere.compte.RoleCabinet;
import com.example.gestionfinanciere.compte.SessionService;

// US-15, US-38, US-16 (espace client du dirigeant) et US-20 (attribution des dossiers) : vrai
// serveur HTTP, migrations Flyway et PostgreSQL 16 jetable.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EspaceClientIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");
	private static final byte[] PDF = "%PDF-1.7\nfacture\n%%EOF".getBytes(StandardCharsets.US_ASCII);
	private static final String LIMITE = "----limite-pointeo";

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private CabinetRepository cabinets;

	@Autowired
	private CompteRepository comptes;

	@Autowired
	private SessionService sessions;

	private final HttpClient http = HttpClient.newHttpClient();

	private static String unique() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private Cabinet cabinet() {
		String id = unique();
		return cabinets.save(new Cabinet("Cabinet Test", String.valueOf(100_000_000
				+ Math.abs(id.hashCode() % 900_000_000)), 120, null, Instant.now(), Instant.now()));
	}

	private Compte collaborateur(Cabinet cabinet, String nom, RoleCabinet role) {
		Compte compte = role == RoleCabinet.ADMINISTRATEUR
				? Compte.administrateurDe(cabinet, unique() + "@cabinet-test.fr", "x", nom)
				: Compte.invite(cabinet, unique() + "@cabinet-test.fr", "x", nom, role);
		compte.activer("x");
		return comptes.save(compte);
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

	private HttpResponse<String> deposer(String sens, String nom, byte[] contenu, String jeton)
			throws IOException, InterruptedException {
		ByteArrayOutputStream corps = new ByteArrayOutputStream();
		corps.writeBytes(("--" + LIMITE + "\r\nContent-Disposition: form-data; name=\"fichier\"; filename=\"" + nom
				+ "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		corps.writeBytes(contenu);
		corps.writeBytes(("\r\n--" + LIMITE + "--\r\n").getBytes(StandardCharsets.UTF_8));
		HttpRequest requete = HttpRequest
				.newBuilder(URI.create("http://localhost:" + port + "/espace-client/factures?sens=" + sens))
				.header("Content-Type", "multipart/form-data; boundary=" + LIMITE)
				.header("Authorization", "Bearer " + jeton)
				.POST(HttpRequest.BodyPublishers.ofByteArray(corps.toByteArray())).build();
		return http.send(requete, HttpResponse.BodyHandlers.ofString());
	}

	private static Long id(HttpResponse<String> reponse) {
		Matcher id = ID.matcher(reponse.body());
		assertThat(id.find()).isTrue();
		return Long.valueOf(id.group(1));
	}

	private Long dossier(String jeton, String raisonSociale) throws IOException, InterruptedException {
		String siren = String.valueOf(100_000_000 + Math.abs(unique().hashCode() % 900_000_000));
		return id(appel("POST", "/dossiers",
				"{\"raisonSociale\":\"" + raisonSociale + "\",\"siren\":\"" + siren + "\"}", jeton));
	}

	// US-15 CA1, CA2 ; US-38 CA1 ; US-16 CA1 et CA2 ; le cabinet voit tout de suite les dépôts.
	@Test
	void depotSuiviEtSuppression() throws Exception {
		Cabinet cabinet = cabinet();
		String lucie = sessions.ouvrir(collaborateur(cabinet, "Lucie Bernard", RoleCabinet.ADMINISTRATEUR));
		Long dossier = dossier(lucie, "Boulangerie Martin");
		Compte dirigeante = Compte.dirigeantInvite(dossier, unique() + "@boulangerie-test.fr", "x", "Sophie Martin");
		dirigeante.activer("x");
		String sophie = sessions.ouvrir(comptes.save(dirigeante));

		HttpResponse<String> achat = deposer("ACHAT", "facture-moulins-bertrand-0142.pdf", PDF, sophie);
		assertThat(achat.statusCode()).isEqualTo(201);
		assertThat(achat.body()).contains("\"nomFichier\":\"facture-moulins-bertrand-0142.pdf\"",
				"\"supprimable\":true");
		Long facture = id(achat);

		HttpResponse<String> refus = deposer("ACHAT", "releve.xlsx", new byte[] { 'P', 'K', 3, 4 }, sophie);
		assertThat(refus.statusCode()).isEqualTo(400);
		assertThat(refus.body()).contains("Format non accepté : PDF, JPG ou PNG uniquement");

		assertThat(deposer("VENTE", "facture-client-FC-2026-031.pdf", PDF, sophie).statusCode()).isEqualTo(201);

		// US-15 RG3 / US-38 CA1 : chaque facture dans son onglet, côté cabinet, avec « Nouvelle ».
		String achatsCabinet = appel("GET", "/dossiers/" + dossier + "/factures?sens=ACHAT", null, lucie).body();
		assertThat(achatsCabinet).contains("facture-moulins-bertrand-0142.pdf", "\"deposeePar\":\"Sophie Martin\"",
				"\"nouvelle\":true").doesNotContain("FC-2026-031");
		assertThat(appel("GET", "/dossiers/" + dossier + "/factures?sens=VENTE", null, lucie).body())
				.contains("FC-2026-031").doesNotContain("moulins");

		// US-16 RG1 : Sophie voit ses dépôts dans chaque onglet.
		assertThat(appel("GET", "/espace-client/factures?sens=ACHAT", null, sophie).body())
				.contains("moulins").doesNotContain("FC-2026-031");

		// US-16 CA2 : Lucie ouvre la facture -> Sophie ne peut plus la supprimer.
		assertThat(appel("GET", "/dossiers/" + dossier + "/factures/" + facture + "/fichier", null, lucie)
				.statusCode()).isEqualTo(200);
		assertThat(appel("GET", "/espace-client/factures?sens=ACHAT", null, sophie).body())
				.contains("\"supprimable\":false");
		assertThat(appel("DELETE", "/espace-client/factures/" + facture, null, sophie).statusCode()).isEqualTo(409);

		// US-16 CA1 : facture jamais ouverte -> supprimée de l'espace et du dossier.
		Long ticket = id(deposer("ACHAT", "ticket-carburant.pdf", PDF, sophie));
		assertThat(appel("DELETE", "/espace-client/factures/" + ticket, null, sophie).statusCode()).isEqualTo(204);
		assertThat(appel("GET", "/espace-client/factures?sens=ACHAT", null, sophie).body())
				.doesNotContain("ticket-carburant");
		assertThat(appel("GET", "/dossiers/" + dossier + "/factures?sens=ACHAT", null, lucie).body())
				.doesNotContain("ticket-carburant");

		// Le cabinet n'a pas accès à l'espace client ; sans connexion non plus.
		assertThat(appel("GET", "/espace-client/factures", null, lucie).statusCode()).isEqualTo(403);
		assertThat(appel("GET", "/espace-client/factures", null, null).statusCode()).isEqualTo(401);
	}

	// US-20 CA1, CA2, RG3 : attribution, accès limité du comptable, attribution automatique.
	@Test
	void attributionDesDossiers() throws Exception {
		Cabinet cabinet = cabinet();
		String jean = sessions.ouvrir(collaborateur(cabinet, "Jean Dupont", RoleCabinet.ADMINISTRATEUR));
		Compte lucieCompte = collaborateur(cabinet, "Lucie Bernard", RoleCabinet.COMPTABLE);
		String lucie = sessions.ouvrir(lucieCompte);
		Long garage = dossier(jean, "Garage Morel SAS");

		// CA2 : non attribué -> introuvable pour Lucie, absent de sa liste.
		HttpResponse<String> refus = appel("GET", "/dossiers/" + garage, null, lucie);
		assertThat(refus.statusCode()).isEqualTo(404);
		assertThat(refus.body()).contains("Dossier introuvable ou accès non autorisé");
		assertThat(appel("GET", "/dossiers/" + garage + "/factures", null, lucie).statusCode()).isEqualTo(404);
		assertThat(appel("GET", "/dossiers", null, lucie).body()).doesNotContain("Garage Morel SAS");
		assertThat(appel("GET", "/dossiers/" + garage + "/comptables", null, jean).body())
				.contains("\"nomAffiche\":\"Lucie Bernard\",\"attribue\":false", "\"modifiable\":true");

		// CA1 : Jean l'attribue à Lucie -> elle le voit.
		HttpResponse<String> attribution = appel("PUT", "/dossiers/" + garage + "/comptables",
				"{\"comptableIds\":[" + lucieCompte.getId() + "]}", jean);
		assertThat(attribution.statusCode()).isEqualTo(200);
		assertThat(attribution.body()).contains("\"attribue\":true");
		assertThat(appel("GET", "/dossiers", null, lucie).body()).contains("Garage Morel SAS",
				"\"comptables\":[\"Lucie Bernard\"]");
		assertThat(appel("GET", "/dossiers/" + garage, null, lucie).statusCode()).isEqualTo(200);
		assertThat(appel("GET", "/dossiers/" + garage + "/comptables", null, lucie).body())
				.contains("\"modifiable\":false");

		// RG1 : Lucie n'attribue pas ; RG3 : le dossier qu'elle crée lui est attribué.
		assertThat(appel("PUT", "/dossiers/" + garage + "/comptables", "{\"comptableIds\":[]}", lucie).statusCode())
				.isEqualTo(403);
		Long sien = dossier(lucie, "Fleurs Martin EURL");
		assertThat(appel("GET", "/dossiers/" + sien, null, lucie).statusCode()).isEqualTo(200);

		// Retrait : liste vide -> Lucie ne voit plus Garage Morel SAS.
		assertThat(appel("PUT", "/dossiers/" + garage + "/comptables", "{\"comptableIds\":[]}", jean).statusCode())
				.isEqualTo(200);
		assertThat(appel("GET", "/dossiers/" + garage, null, lucie).statusCode()).isEqualTo(404);
	}

}
