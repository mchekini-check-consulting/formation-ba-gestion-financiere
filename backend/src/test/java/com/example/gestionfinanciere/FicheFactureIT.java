package com.example.gestionfinanciere;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.awaitility.Awaitility;
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

// US-55 Fiche facture : vrai serveur HTTP, migrations Flyway (V26__correction_facture.sql), PostgreSQL 16
// jetable et lecture automatique réelle de la facture EDF d'exemple (mode test).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "pointeo.mode-test=true")
@Testcontainers
class FicheFactureIT {

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

	// Lucie, collaboratrice d'un nouveau cabinet, connectée : renvoie son jeton de session.
	private String lucieConnectee() {
		String id = UUID.randomUUID().toString().substring(0, 8);
		Cabinet cabinet = cabinets.save(new Cabinet("Cabinet Test", String.valueOf(100_000_000
				+ Math.abs(id.hashCode() % 900_000_000)), 120, null, Instant.now(), Instant.now()));
		Compte lucie = comptes.save(Compte.administrateurDe(cabinet, "lucie+" + id + "@cabinet-test.fr", "x",
				"Lucie Moreau"));
		return sessions.ouvrir(lucie);
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
				{"raisonSociale":"Boulangerie Dupont","siren":"812345679"}""", jeton);
		Matcher id = ID.matcher(creation.body());
		assertThat(id.find()).isTrue();
		return "/dossiers/" + id.group(1);
	}

	private static Long facture(String liste, String nomFichier) {
		Matcher id = Pattern.compile("\"id\":(\\d+),\"nomFichier\":\"" + Pattern.quote(nomFichier) + "\"")
				.matcher(liste);
		assertThat(id.find()).isTrue();
		return Long.valueOf(id.group(1));
	}

	// Attend la fin de la lecture automatique (arrière-plan), 30 secondes au plus.
	private String apresLecture(String chemin, String jeton) {
		AtomicReference<String> page = new AtomicReference<>("");
		Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).until(() -> {
			page.set(appel("GET", chemin, null, jeton).body());
			return !page.get().contains("\"lecture\":{\"statut\":\"EN_COURS\"");
		});
		return page.get();
	}

	// Champs de la facture EDF (CA1), avec le numéro, le TTC et la contrepartie donnés.
	private static String fiche(String numero, String ttc, String compte) {
		return """
				{"typeTiers":"FOURNISSEUR","nomTiers":"EDF","numeroFacture":%s,"dateEmission":"2026-06-12",
				"montantHt":155.33,"montantTva":31.07,"tauxTva":20.00,"montantTtc":%s,"compteContrepartie":"%s"}"""
				.formatted(numero == null ? "null" : "\"" + numero + "\"", ttc, compte);
	}

	// CA1 à CA3, RG2, RG3, RG6, RG8 : la facture EDF lue est corrigée, refusée tant qu'elle est fausse,
	// validée, puis en lecture seule.
	@Test
	void correctionEtValidation() throws Exception {
		String jeton = lucieConnectee();
		String dossier = dossier(jeton);
		assertThat(appel("POST", dossier + "/factures/exemples", null, jeton).statusCode()).isEqualTo(201);
		String liste = appel("GET", dossier + "/factures?sens=ACHAT", null, jeton).body();
		Long edf = facture(liste, "facture-electricite-octobre.pdf");
		Long ticket = facture(liste, "ticket-fournitures.png");
		String chemin = dossier + "/factures/" + edf;

		// RG1 : la fiche reprend les champs lus ; contrepartie proposée 6061, absente du plan du dossier.
		assertThat(apresLecture(chemin, jeton)).contains("\"lecture\":{\"statut\":\"LUE\"",
				"\"fiche\":{\"statutTraitement\":\"RECUE\",\"motif\":null,\"modifiable\":true",
				"\"compteContrepartie\":\"6061\"");

		// CA3, CA2, RG3 : refus avec le message ; le statut ne change pas.
		HttpResponse<String> incoherente = appel("POST", chemin + "/validation", fiche("F-2026-0611", "190.00", "606"),
				jeton);
		assertThat(incoherente.statusCode()).isEqualTo(400);
		assertThat(incoherente.body()).contains("Montants incohérents : HT + TVA ≠ TTC");
		assertThat(appel("POST", chemin + "/validation", fiche(null, "186.40", "606"), jeton).body())
				.contains("Indiquez le numéro de facture");
		assertThat(appel("POST", chemin + "/validation", fiche("F-2026-0611", "186.40", "6061"), jeton).body())
				.contains("Le compte 6061 n'est pas dans le plan comptable du dossier");
		assertThat(appel("GET", chemin, null, jeton).body()).contains("\"statutTraitement\":\"RECUE\"");

		// RG2 : corrections enregistrées sans valider.
		HttpResponse<String> enregistree = appel("PUT", chemin + "/informations", fiche("F-2026-0611", "186.40", "606"),
				jeton);
		assertThat(enregistree.statusCode()).isEqualTo(200);
		assertThat(enregistree.body()).contains("\"statutTraitement\":\"RECUE\"", "\"compteContrepartie\":\"606\"",
				"\"suivante\":null");

		// CA1, RG6 : validée, « Traitée », avec la facture suivante à traiter (le ticket).
		HttpResponse<String> validee = appel("POST", chemin + "/validation", fiche("F-2026-0611", "186.40", "606"),
				jeton);
		assertThat(validee.statusCode()).isEqualTo(200);
		assertThat(validee.body()).contains("\"statutTraitement\":\"TRAITEE\",\"motif\":null,\"modifiable\":false",
				"\"montantTtc\":186.40", "\"suivante\":" + ticket);
		assertThat(appel("GET", dossier + "/factures?sens=ACHAT", null, jeton).body()).contains(
				"\"id\":" + edf + ",\"nomFichier\":\"facture-electricite-octobre.pdf\"", "\"statut\":\"TRAITEE\"");

		// RG8 : facture traitée en lecture seule.
		HttpResponse<String> traitee = appel("PUT", chemin + "/informations", fiche("F-2026-0611", "186.40", "606"),
				jeton);
		assertThat(traitee.statusCode()).isEqualTo(409);
		assertThat(traitee.body()).contains("n'est plus modifiable");
	}

	// Droits du dossier : autre cabinet, sans connexion, adresse tapée à la main.
	@Test
	void controleDAcces() throws Exception {
		String jeton = lucieConnectee();
		String dossier = dossier(jeton);
		appel("POST", dossier + "/factures/exemples", null, jeton);
		Long ticket = facture(appel("GET", dossier + "/factures?sens=ACHAT", null, jeton).body(),
				"ticket-fournitures.png");
		String chemin = dossier + "/factures/" + ticket;
		String corps = fiche("T-1", "186.40", "606");

		HttpResponse<String> autreCabinet = appel("PUT", chemin + "/informations", corps, lucieConnectee());
		assertThat(autreCabinet.statusCode()).isEqualTo(404);
		assertThat(autreCabinet.body()).contains("Facture introuvable ou accès non autorisé");
		assertThat(appel("POST", chemin + "/validation", corps, null).statusCode()).isEqualTo(401);
		assertThat(appel("POST", dossier + "/factures/abc/validation", corps, jeton).statusCode()).isEqualTo(404);
	}

}
