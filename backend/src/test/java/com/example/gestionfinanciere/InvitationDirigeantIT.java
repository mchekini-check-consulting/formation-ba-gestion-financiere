package com.example.gestionfinanciere;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.cabinet.CabinetRepository;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.CompteRepository;
import com.example.gestionfinanciere.compte.Jetons;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.equipe.Invitation;
import com.example.gestionfinanciere.equipe.InvitationRepository;
import com.example.gestionfinanciere.notification.EmailsRecus;
import com.example.gestionfinanciere.notification.EnvoiEmail;

// US-14 Inviter le dirigeant : vrai serveur HTTP, migrations Flyway et PostgreSQL 16 jetable, en
// mode test (lien d'invitation renvoyé à l'écran, aucune API d'envoi d'emails branchée).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class InvitationDirigeantIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

	// Emails simulés : le code ou le lien est lu dans l'email envoyé, jamais dans la réponse.
	@MockitoBean
	private EnvoiEmail envoiEmail;

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private CabinetRepository cabinets;

	@Autowired
	private CompteRepository comptes;

	@Autowired
	private InvitationRepository invitations;

	@Autowired
	private SessionService sessions;

	private final HttpClient http = HttpClient.newHttpClient();

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

	private Long dossier(String jeton) throws IOException, InterruptedException {
		Matcher id = ID.matcher(appel("POST", "/dossiers",
				"{\"raisonSociale\":\"Boulangerie Leroy SARL\",\"siren\":\"812345678\"}", jeton).body());
		assertThat(id.find()).isTrue();
		return Long.valueOf(id.group(1));
	}

	// Invitation envoyée : jeton lu dans le lien de l'email, jamais dans la réponse.
	private String jeton(HttpResponse<String> reponse) {
		assertThat(reponse.body()).contains("INVITE").doesNotContain("jeton");
		return EmailsRecus.dernierJeton(envoiEmail);
	}

	private static String sophie(String email) {
		return "{\"prenom\":\"Sophie\",\"nom\":\"Martin\",\"email\":\"" + email + "\"}";
	}

	// RG1, RG2, RG3 : Non invité -> Invité ; renvoi : nouveau lien, l'ancien annulé.
	@Test
	void invitationPuisRenvoi() throws Exception {
		String jeton = jeanConnecte();
		Long dossier = dossier(jeton);
		String chemin = "/dossiers/" + dossier + "/invitation";
		String email = "sophie+" + dossier + "@boulangerie-test.fr";

		// CA erreur.
		HttpResponse<String> invalide = appel("POST", chemin, sophie("sophie@"), jeton);
		assertThat(invalide.statusCode()).isEqualTo(400);
		assertThat(invalide.body()).contains("Saisissez une adresse email valide");

		HttpResponse<String> envoi = appel("POST", chemin, sophie(email), jeton);
		assertThat(envoi.statusCode()).isEqualTo(201);
		assertThat(envoi.body()).contains("\"etatEspaceClient\":\"INVITE\"");
		String premier = jeton(envoi);
		assertThat(appel("GET", "/dossiers/" + dossier, null, jeton).body()).contains("\"etatEspaceClient\":\"INVITE\"");
		assertThat(appel("GET", "/dossiers", null, jeton).body()).contains("\"etatEspaceClient\":\"INVITE\"");

		// Déjà invité : on renvoie, on n'invite pas une seconde fois.
		HttpResponse<String> deuxieme = appel("POST", chemin, sophie("autre+" + dossier + "@test.fr"), jeton);
		assertThat(deuxieme.statusCode()).isEqualTo(409);
		assertThat(deuxieme.body()).contains("Le dirigeant de ce dossier est déjà invité");

		HttpResponse<String> renvoi = appel("POST", chemin + "/renvoi", null, jeton);
		assertThat(renvoi.statusCode()).isEqualTo(200);
		String second = jeton(renvoi);
		assertThat(second).isNotEqualTo(premier);

		Compte dirigeante = comptes.findByEmail(email).orElseThrow();
		assertThat(dirigeante.getDossierId()).isEqualTo(dossier);
		assertThat(dirigeante.isActif()).isFalse();
		List<Invitation> valables = invitations.findByCompteIdAndUtiliseeLeIsNullAndAnnuleeLeIsNull(
				dirigeante.getId());
		// Seule la nouvelle invitation reste valable ; aucune ne stocke le jeton en clair.
		assertThat(valables).extracting(Invitation::getEmpreinteJeton).containsExactly(Jetons.empreinte(second));
		assertThat(invitations.findAll()).extracting(Invitation::getEmpreinteJeton).doesNotContain(premier, second);
	}

	// Droits du dossier : autre cabinet, sans connexion ; renvoi sans invitation en attente.
	@Test
	void controleDAcces() throws Exception {
		String jeton = jeanConnecte();
		Long dossier = dossier(jeton);
		String chemin = "/dossiers/" + dossier + "/invitation";

		HttpResponse<String> autreCabinet = appel("POST", chemin, sophie("s+" + dossier + "@test.fr"), jeanConnecte());
		assertThat(autreCabinet.statusCode()).isEqualTo(404);
		assertThat(autreCabinet.body()).contains("Dossier introuvable ou accès non autorisé");
		assertThat(appel("POST", chemin, sophie("s+" + dossier + "@test.fr"), null).statusCode()).isEqualTo(401);

		HttpResponse<String> renvoi = appel("POST", chemin + "/renvoi", null, jeton);
		assertThat(renvoi.statusCode()).isEqualTo(409);
		assertThat(renvoi.body()).contains("Aucune invitation en attente pour ce dossier");
		assertThat(appel("GET", "/dossiers/" + dossier, null, jeton).body())
				.contains("\"etatEspaceClient\":\"NON_INVITE\"");
	}

}
