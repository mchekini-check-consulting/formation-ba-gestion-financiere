package com.example.gestionfinanciere;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.cabinet.CabinetRepository;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.CompteRepository;
import com.example.gestionfinanciere.compte.SessionService;

// US-12 Factures d'un dossier : vrai serveur HTTP, migrations Flyway et PostgreSQL 16 jetable.
// Les factures sont importées par le cabinet (US-52) à partir des fichiers d'exemple des tests.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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

	@Autowired
	private JdbcTemplate jdbc;

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

	// Deux factures fournisseurs (l'électricité puis le ticket) et une facture client, importées par le cabinet.
	private void exemples(String dossier, String jeton) throws IOException, InterruptedException {
		for (String[] exemple : new String[][] { { "ACHAT", "facture-electricite-octobre.pdf" },
				{ "ACHAT", "ticket-fournitures.png" }, { "VENTE", "facture-client-0042.pdf" } }) {
			HttpResponse<String> import_ = importer(dossier + "/factures?sens=" + exemple[0], exemple[1],
					fichierDExemple(exemple[1]), jeton);
			assertThat(import_.statusCode()).isEqualTo(201);
		}
	}

	private static byte[] fichierDExemple(String nom) throws IOException {
		try (var flux = FactureIT.class.getResourceAsStream("/exemples/" + nom)) {
			return flux.readAllBytes();
		}
	}

	// CA vide, puis RG1 (achats / ventes), RG2 (fichier, date, déposée par, « Reçue »), RG3 (« Nouvelle »).
	@Test
	void facturesDUnDossier() throws Exception {
		String jeton = jeanConnecte();
		String dossier = dossier(jeton);

		assertThat(appel("GET", dossier + "/factures?sens=ACHAT", null, jeton).body())
				.isEqualTo("{\"factures\":[]}");

		exemples(dossier, jeton);

		HttpResponse<String> achats = appel("GET", dossier + "/factures?sens=ACHAT", null, jeton);
		assertThat(achats.statusCode()).isEqualTo(200);
		// La plus récente d'abord : le ticket, puis l'électricité ; personne ne les a ouvertes.
		assertThat(achats.body()).containsSubsequence("\"nomFichier\":\"ticket-fournitures.png\"",
				"\"deposeePar\":\"Jean Dupont\",\"statut\":\"RECUE\",\"nouvelle\":true",
				"\"nomFichier\":\"facture-electricite-octobre.pdf\"", "\"nouvelle\":true");
		assertThat(achats.body()).doesNotContain("facture-client-0042.pdf");

		// Sans « sens » : factures fournisseurs (onglet par défaut). Le statut de lecture (US-53) peut avoir
		// avancé entre les deux appels (lecture en arrière-plan) : comparé sans lui.
		assertThat(sansLecture(appel("GET", dossier + "/factures", null, jeton).body()))
				.isEqualTo(sansLecture(achats.body()));

		HttpResponse<String> ventes = appel("GET", dossier + "/factures?sens=VENTE", null, jeton);
		assertThat(ventes.body()).contains("facture-client-0042.pdf").doesNotContain("ticket-fournitures.png");

		assertThat(appel("GET", dossier + "/factures?sens=AUTRE", null, jeton).statusCode()).isEqualTo(400);
	}

	// Droits du dossier : autre cabinet ou sans connexion -> aucune facture.
	@Test
	void controleDAcces() throws Exception {
		String jeton = jeanConnecte();
		String dossier = dossier(jeton);
		exemples(dossier, jeton);

		String autreCabinet = jeanConnecte();
		HttpResponse<String> refus = appel("GET", dossier + "/factures?sens=ACHAT", null, autreCabinet);
		assertThat(refus.statusCode()).isEqualTo(404);
		assertThat(refus.body()).contains("Dossier introuvable ou accès non autorisé").doesNotContain("ticket");

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

	private static String sansLecture(String json) {
		return json.replaceAll("\"lecture\":(null|\"[A-Z_]+\"),\"lectureAVerifier\":(true|false),"
				+ "\"nomTiers\":(null|\"[^\"]*\"),\"dateEmission\":(null|\"[^\"]*\"),\"montantTtc\":(null|[0-9.]+)", "");
	}

	private static String debut(byte[] contenu, int taille) {
		return new String(contenu, 0, Math.min(taille, contenu.length), StandardCharsets.ISO_8859_1);
	}

	// US-13 RG1 (aperçu), RG2 (fichier d'origine) et badge « Nouvelle » retiré à l'ouverture.
	@Test
	void us13ApercuEtTelechargement() throws Exception {
		String jeton = jeanConnecte();
		String dossier = dossier(jeton);
		exemples(dossier, jeton);
		Long ticket = premiereFacture(dossier, "ACHAT", jeton);
		String chemin = dossier + "/factures/" + ticket;

		// Suivi de la lecture automatique (US-53) : vérifié dans EspaceClientIT.
		assertThat(appel("GET", chemin, null, jeton).body()).startsWith(
				"{\"id\":%d,\"nomFichier\":\"ticket-fournitures.png\",\"typeFichier\":\"image/png\",\"sens\":\"ACHAT\","
						.formatted(ticket));

		HttpResponse<byte[]> apercu = fichier(chemin + "/fichier", jeton);
		assertThat(apercu.statusCode()).isEqualTo(200);
		assertThat(apercu.headers().firstValue("Content-Type")).hasValue("image/png");
		assertThat(apercu.headers().firstValue("Content-Disposition").orElseThrow()).startsWith("inline;");
		assertThat(debut(apercu.body(), 4)).isEqualTo("\u0089PNG");
		// Le ticket ouvert n'est plus « Nouvelle » ; l'électricité, jamais ouverte, le reste.
		assertThat(appel("GET", dossier + "/factures?sens=ACHAT", null, jeton).body()).containsSubsequence(
				"\"nomFichier\":\"ticket-fournitures.png\"", "\"nouvelle\":false",
				"\"nomFichier\":\"facture-electricite-octobre.pdf\"", "\"nouvelle\":true");

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
		exemples(dossier, jeton);
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

	// US-52 : envoi d'un fichier en multipart, comme l'écran « Importer des factures ».
	private HttpResponse<String> importer(String chemin, String nom, byte[] contenu, String jeton)
			throws IOException, InterruptedException {
		String limite = "----pointeo" + UUID.randomUUID();
		byte[] debut = ("--" + limite + "\r\nContent-Disposition: form-data; name=\"fichier\"; filename=\"" + nom
				+ "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
		byte[] fin = ("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8);
		byte[] corps = new byte[debut.length + contenu.length + fin.length];
		System.arraycopy(debut, 0, corps, 0, debut.length);
		System.arraycopy(contenu, 0, corps, debut.length, contenu.length);
		System.arraycopy(fin, 0, corps, debut.length + contenu.length, fin.length);
		HttpRequest requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
				.header("Content-Type", "multipart/form-data; boundary=" + limite)
				.header("Authorization", "Bearer " + jeton).POST(HttpRequest.BodyPublishers.ofByteArray(corps))
				.build();
		return http.send(requete, HttpResponse.BodyHandlers.ofString());
	}

	// US-52 CA1 (import « Reçue », « Import cabinet »), CA2 (mauvais format), CA3 (dossier d'un autre cabinet).
	@Test
	void importParLeCabinet() throws Exception {
		String jeton = jeanConnecte();
		String dossier = dossier(jeton);

		HttpResponse<String> importee = importer(dossier + "/factures?sens=ACHAT", "facture-edf-juin.pdf",
				"%PDF-1.7 facture".getBytes(StandardCharsets.US_ASCII), jeton);
		assertThat(importee.statusCode()).isEqualTo(201);
		assertThat(importee.body()).contains("\"nomFichier\":\"facture-edf-juin.pdf\"", "\"statut\":\"RECUE\"",
				"\"source\":\"IMPORT_CABINET\"");

		HttpResponse<String> achats = appel("GET", dossier + "/factures?sens=ACHAT", null, jeton);
		assertThat(achats.body()).contains("facture-edf-juin.pdf", "\"source\":\"IMPORT_CABINET\"");
		assertThat(appel("GET", dossier + "/factures?sens=VENTE", null, jeton).body())
				.doesNotContain("facture-edf-juin.pdf");

		HttpResponse<String> xlsx = importer(dossier + "/factures?sens=VENTE", "releve.xlsx",
				new byte[] { 'P', 'K', 3, 4, 0, 0 }, jeton);
		assertThat(xlsx.statusCode()).isEqualTo(400);
		assertThat(xlsx.body()).contains("Format non accepté : PDF, JPG ou PNG uniquement");

		String autre = jeanConnecte();
		HttpResponse<String> refuse = importer(dossier + "/factures?sens=ACHAT", "edf.pdf",
				"%PDF-1.7".getBytes(StandardCharsets.US_ASCII), autre);
		assertThat(refuse.statusCode()).isEqualTo(404);
	}


	// US-55 CA1 à CA3 : la facture EDF lue, corrigée puis validée ; une facture traitée ne se modifie plus.
	@Test
	void us55CorrectionEtValidation() throws Exception {
		String jeton = jeanConnecte();
		String dossier = dossier(jeton);
		HttpResponse<String> importee = importer(dossier + "/factures?sens=ACHAT", "facture-electricite-octobre.pdf",
				fichierDExemple("facture-electricite-octobre.pdf"), jeton);
		Matcher id = ID.matcher(importee.body());
		assertThat(id.find()).isTrue();
		String fiche = dossier + "/factures/" + id.group(1);
		Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
				.until(() -> !appel("GET", fiche, null, jeton).body().contains("\"statut\":\"EN_COURS\""));

		String incoherente = """
				{"typeTiers":"FOURNISSEUR","nomTiers":"EDF","numeroFacture":"F-2026-0611","dateEmission":"2026-06-12",
				"montantHt":155.33,"montantTva":31.07,"tauxTva":20,"montantTtc":190.00,"compteContrepartie":"606"}""";
		HttpResponse<String> refus = appel("POST", fiche + "/validation", incoherente, jeton);
		assertThat(refus.statusCode()).isEqualTo(400);
		assertThat(refus.body()).contains("Montants incohérents : HT + TVA ≠ TTC");
		assertThat(appel("POST", fiche + "/validation", "{\"nomTiers\":\"EDF\"}", jeton).body())
				.contains("Indiquez le numéro de facture");

		// La lecture a déjà trouvé ces montants : seul le nom du tiers change (RG8).
		String corrigee = incoherente.replace("190.00", "186.40").replace("\"EDF\"", "\"EDF Entreprises\"");
		HttpResponse<String> enregistree = appel("PUT", fiche + "/informations", corrigee, jeton);
		assertThat(enregistree.statusCode()).isEqualTo(200);
		assertThat(enregistree.body()).contains("\"nomTiers\":\"EDF Entreprises\"", "\"statut\":\"RECUE\"",
				"\"modification\":{\"par\":\"Jean Dupont\"");

		HttpResponse<String> validee = appel("POST", fiche + "/validation", corrigee, jeton);
		assertThat(validee.statusCode()).isEqualTo(200);
		assertThat(validee.body()).contains("\"statut\":\"TRAITEE\"");
		assertThat(appel("GET", dossier + "/factures?sens=ACHAT", null, jeton).body())
				.contains("\"statut\":\"TRAITEE\"");

		// US-56 : une facture d'achat traitée se corrige encore, mais reste complète et cohérente.
		HttpResponse<String> incoherenteApres = appel("PUT", fiche + "/informations", incoherente, jeton);
		assertThat(incoherenteApres.statusCode()).isEqualTo(400);
		assertThat(incoherenteApres.body()).contains("Montants incohérents : HT + TVA ≠ TTC");
		assertThat(appel("POST", fiche + "/validation", corrigee, jeton).statusCode()).isEqualTo(409);
		assertThat(appel("PUT", fiche + "/informations", incoherente, jeanConnecte()).statusCode()).isEqualTo(404);
	}

	// US-56 CA1 à CA3 : écriture générée à la validation, recalculée après correction, figée une fois
	// exportée ; contrôle SQL de l'US-30 (zéro écriture déséquilibrée, zéro compte absent du plan).
	@Test
	void us56EcrituresDUneFactureValidee() throws Exception {
		String jeton = jeanConnecte();
		String dossier = dossier(jeton);
		HttpResponse<String> importee = importer(dossier + "/factures?sens=ACHAT", "facture-electricite-octobre.pdf",
				fichierDExemple("facture-electricite-octobre.pdf"), jeton);
		Matcher id = ID.matcher(importee.body());
		assertThat(id.find()).isTrue();
		String fiche = dossier + "/factures/" + id.group(1);
		Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
				.until(() -> !appel("GET", fiche, null, jeton).body().contains("\"statut\":\"EN_COURS\""));
		assertThat(appel("POST", fiche + "/ecriture", null, jeton).body())
				.contains("Validez la facture pour générer ses écritures");

		String edf = """
				{"typeTiers":"FOURNISSEUR","nomTiers":"EDF","numeroFacture":"F-2026-0611","dateEmission":"2026-06-12",
				"montantHt":155.33,"montantTva":31.07,"tauxTva":20,"montantTtc":186.40,"compteContrepartie":"607"}""";
		HttpResponse<String> validee = appel("POST", fiche + "/validation", edf, jeton);
		assertThat(validee.statusCode()).isEqualTo(200);
		assertThat(validee.body()).contains("\"journal\":\"AC\"", "\"date\":\"2026-06-12\"",
				"\"libelle\":\"EDF F-2026-0611\"", "\"aJour\":true", "\"exportee\":false",
				"{\"numeroCompte\":\"607\",\"libelleCompte\":\"Achats de marchandises\",\"libelle\":\"EDF F-2026-0611\",\"debit\":155.33,\"credit\":0.00}",
				"{\"numeroCompte\":\"44566\"", "\"debit\":31.07",
				"{\"numeroCompte\":\"401\",\"libelleCompte\":\"Fournisseurs\",\"libelle\":\"EDF F-2026-0611\",\"debit\":0.00,\"credit\":186.40}",
				"\"totalDebit\":186.40,\"totalCredit\":186.40");
		controleSql();

		// CA2 : HT corrigé, écriture « à re-générer », puis recalculée et toujours équilibrée.
		String htCorrige = edf.replace("155.33", "160.00").replace("186.40", "191.07");
		assertThat(appel("PUT", fiche + "/informations", htCorrige, jeton).body()).contains("\"aJour\":false");
		HttpResponse<String> regeneree = appel("POST", fiche + "/ecriture", null, jeton);
		assertThat(regeneree.statusCode()).isEqualTo(200);
		assertThat(regeneree.body()).contains("\"aJour\":true", "\"debit\":160.00",
				"\"totalDebit\":191.07,\"totalCredit\":191.07");
		assertThat(jdbc.queryForObject("select count(*) from ligne_ecriture l join ecriture e on e.id = l.ecriture_id"
				+ " where e.facture_id = ?", Integer.class, Long.valueOf(id.group(1)))).isEqualTo(3);
		controleSql();

		// RG3 du plan comptable : le 607 utilisé ne se supprime pas (compte général) ; un sous-compte utilisé non plus.
		assertThat(appel("POST", dossier + "/plan-comptable", """
				{"numero":"401EDF","libelle":"EDF"}""", jeton).statusCode()).isEqualTo(201);
		jdbc.update("update ligne_ecriture set numero_compte = '401EDF' where numero_compte = '401'"
				+ " and ecriture_id = (select id from ecriture where facture_id = ?)", Long.valueOf(id.group(1)));
		HttpResponse<String> utilise = appel("DELETE", dossier + "/plan-comptable/401EDF", null, jeton);
		assertThat(utilise.statusCode()).isEqualTo(409);
		assertThat(utilise.body()).contains("utilisé dans une écriture");

		// CA3 : écriture exportée, plus de re-génération ni de correction.
		jdbc.update("update ecriture set exportee_le = now() where facture_id = ?", Long.valueOf(id.group(1)));
		HttpResponse<String> exportee = appel("POST", fiche + "/ecriture", null, jeton);
		assertThat(exportee.statusCode()).isEqualTo(409);
		assertThat(exportee.body()).contains("Écriture exportée : la facture ne se modifie plus");
		assertThat(appel("GET", fiche, null, jeton).body()).contains("\"exportee\":true");
		assertThat(appel("PUT", fiche + "/informations", edf, jeton).statusCode()).isEqualTo(409);
	}

	// US-47 RG1 à RG4, CA1 à CA3 : journal du dossier, correction d'une écriture, refus, écriture exportée.
	@Test
	void us47JournalEtCorrection() throws Exception {
		String jeton = jeanConnecte();
		String dossier = dossier(jeton);
		assertThat(appel("GET", dossier + "/ecritures", null, jeton).body()).contains("\"periode\":null",
				"\"ecritures\":[]");
		HttpResponse<String> importee = importer(dossier + "/factures?sens=ACHAT", "facture-electricite-octobre.pdf",
				fichierDExemple("facture-electricite-octobre.pdf"), jeton);
		Matcher id = ID.matcher(importee.body());
		assertThat(id.find()).isTrue();
		String fiche = dossier + "/factures/" + id.group(1);
		Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
				.until(() -> !appel("GET", fiche, null, jeton).body().contains("\"statut\":\"EN_COURS\""));
		assertThat(appel("POST", fiche + "/validation", """
				{"typeTiers":"FOURNISSEUR","nomTiers":"EDF","numeroFacture":"F-2026-0611","dateEmission":"2026-06-12",
				"montantHt":155.33,"montantTva":31.07,"tauxTva":20,"montantTtc":186.40,"compteContrepartie":"607"}""",
				jeton).statusCode()).isEqualTo(200);

		// RG1 : journal du dernier mois, n° de pièce = n° de facture.
		HttpResponse<String> juin = appel("GET", dossier + "/ecritures", null, jeton);
		assertThat(juin.statusCode()).isEqualTo(200);
		assertThat(juin.body()).contains("\"periode\":\"2026-06\"", "{\"code\":\"2026-06\",\"libelle\":\"Juin 2026\"}",
				"{\"code\":\"2026\",\"libelle\":\"Exercice 2026\"}", "\"piece\":\"F-2026-0611\"",
				"\"exportee\":false", "\"modifieePar\":null", "\"totalDebit\":186.40,\"totalCredit\":186.40");
		Long ecriture = jdbc.queryForObject("select id from ecriture where facture_id = ?", Long.class,
				Long.valueOf(id.group(1)));
		String route = dossier + "/ecritures/" + ecriture;

		// CA2 : débit à 196,40 sans changer le crédit : refusé, rien n'est enregistré.
		String corrigee = """
				{"libelle":"EDF – facture F-2026-0611","lignes":[{"numeroCompte":"6061","debit":155.33},
				{"numeroCompte":"44566","debit":31.07},{"numeroCompte":"401","credit":186.40}]}""";
		HttpResponse<String> desequilibree = appel("PUT", route, corrigee.replace("155.33", "165.33"), jeton);
		assertThat(desequilibree.statusCode()).isEqualTo(400);
		assertThat(desequilibree.body()).contains("Écriture déséquilibrée : écart de 10,00 €");

		// RG3 : 6061 pas encore dans le plan du dossier.
		assertThat(appel("PUT", route, corrigee, jeton).body())
				.contains("Le compte 6061 n'est pas dans le plan comptable du dossier");
		assertThat(jdbc.queryForObject("select modifiee_le is null from ecriture where id = ?", Boolean.class, ecriture))
				.isTrue();

		// CA1, RG4 : 607 remplacé par 6061, auteur et date gardés ; la fiche facture reste à jour.
		assertThat(appel("POST", dossier + "/plan-comptable", """
				{"numero":"6061","libelle":"Fournitures non stockables (eau, énergie)"}""", jeton).statusCode())
				.isEqualTo(201);
		HttpResponse<String> modifiee = appel("PUT", route, corrigee, jeton);
		assertThat(modifiee.statusCode()).isEqualTo(200);
		assertThat(modifiee.body()).contains("\"libelle\":\"EDF – facture F-2026-0611\"",
				"{\"numeroCompte\":\"6061\",\"libelleCompte\":\"Fournitures non stockables (eau, énergie)\"",
				"\"modifieePar\":\"Jean Dupont\"");
		assertThat(appel("GET", fiche, null, jeton).body()).contains("\"aJour\":true", "\"modifieePar\":\"Jean Dupont\"");
		controleSql();

		// CA3 : écriture exportée, en lecture seule.
		jdbc.update("update ecriture set exportee_le = now() where id = ?", ecriture);
		assertThat(appel("GET", dossier + "/ecritures?periode=2026", null, jeton).body()).contains("\"exportee\":true");
		HttpResponse<String> exportee = appel("PUT", route, corrigee, jeton);
		assertThat(exportee.statusCode()).isEqualTo(409);
		assertThat(exportee.body()).contains("Écriture exportée : elle ne se modifie plus");
		assertThat(appel("PUT", dossier + "/ecritures/999999", corrigee, jeton).statusCode()).isEqualTo(404);
	}

	// US-56 / US-30 : les deux requêtes de contrôle renvoient zéro ligne.
	private void controleSql() {
		assertThat(jdbc.queryForList("""
				select e.id from ecriture e join ligne_ecriture l on l.ecriture_id = e.id
				group by e.id having sum(l.debit) <> sum(l.credit)""")).isEmpty();
		assertThat(jdbc.queryForList("""
				select l.id from ligne_ecriture l join ecriture e on e.id = l.ecriture_id
				left join compte_comptable c on c.dossier_id = e.dossier_id and c.numero = l.numero_compte
				where c.id is null""")).isEmpty();
	}

}
