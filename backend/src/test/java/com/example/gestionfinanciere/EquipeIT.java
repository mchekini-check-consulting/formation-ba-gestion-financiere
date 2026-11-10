package com.example.gestionfinanciere;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

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
import com.example.gestionfinanciere.compte.RoleCabinet;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.notification.EmailsRecus;
import com.example.gestionfinanciere.notification.EnvoiEmail;

// US-19 Inviter un collaborateur avec un rôle (PTO-26) : vrai serveur HTTP, migrations Flyway
// (dont V9) et PostgreSQL 16 jetable. Chaque test crée son propre cabinet.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EquipeIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

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
	private SessionService sessions;

	private final HttpClient http = HttpClient.newHttpClient();

	// Claire, administratrice d'un nouveau cabinet, connectée : renvoie son jeton de session.
	private String claireConnectee() {
		String id = UUID.randomUUID().toString().substring(0, 8);
		Cabinet cabinet = cabinets.save(new Cabinet("Cabinet Test", String.valueOf(100_000_000
				+ Math.abs(id.hashCode() % 900_000_000)), 120, null, Instant.now(), Instant.now()));
		Compte claire = comptes.save(Compte.administrateurDe(cabinet, "claire+" + id + "@cabinet-test.fr", "x",
				"Claire Martin"));
		return sessions.ouvrir(claire);
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

	private HttpResponse<String> inviter(String jeton, String email, String role)
			throws IOException, InterruptedException {
		return appel("POST", "/equipe/invitations", """
				{"prenom":"Lucie","nom":"Moreau","email":"%s","role":"%s"}""".formatted(email, role), jeton);
	}

	@Test
	void ca1ClaireInviteLucieCommeComptable() throws Exception {
		String jeton = claireConnectee();
		String email = "lucie.moreau+" + UUID.randomUUID() + "@cabinet-test.fr";

		HttpResponse<String> invitation = inviter(jeton, email, "COMPTABLE");
		assertThat(invitation.statusCode()).isEqualTo(201);
		assertThat(invitation.body()).contains("\"statut\":\"INVITE\"", "\"role\":\"COMPTABLE\"")
				.doesNotContain("jeton");
		assertThat(EmailsRecus.dernierJeton(envoiEmail, email)).isNotBlank();

		HttpResponse<String> equipe = appel("GET", "/equipe", null, jeton);
		assertThat(equipe.statusCode()).isEqualTo(200);
		assertThat(equipe.body()).contains("Claire Martin", "\"ADMINISTRATEUR\"", "Lucie Moreau", email);

		Compte lucie = comptes.findByEmail(email).orElseThrow();
		assertThat(lucie.isActif()).isFalse();
		assertThat(lucie.getRoleCabinet()).isEqualTo(RoleCabinet.COMPTABLE);

		// Tant que le compte n'est pas activé (US-08), Lucie ne peut pas se connecter.
		HttpResponse<String> connexion = appel("POST", "/auth/connexion",
				"{\"email\":\"" + email + "\",\"motDePasse\":\"Pointeo2026!!\"}", null);
		assertThat(connexion.statusCode()).isEqualTo(401);

		// La session de Claire indique qu'elle est administratrice (menu Équipe).
		assertThat(appel("GET", "/auth/session", null, jeton).body()).contains("\"administrateur\":true");
	}

	@Test
	void ca2AdresseDejaUtiliseeParUnCompteClient() throws Exception {
		HttpResponse<String> reponse = inviter(claireConnectee(), "Sophie.Martin@boulangerie-test.fr", "ASSOCIE");
		assertThat(reponse.statusCode()).isEqualTo(409);
		assertThat(reponse.body()).contains("Cette adresse est déjà utilisée");
	}

	@Test
	void rg1ReserveAuxAdministrateurs() throws Exception {
		String jeton = claireConnectee();
		String email = "associe+" + UUID.randomUUID() + "@cabinet-test.fr";
		inviter(jeton, email, "ASSOCIE");
		Compte associe = comptes.findByEmail(email).orElseThrow();

		assertThat(appel("GET", "/equipe", null, sessions.ouvrir(associe)).statusCode()).isEqualTo(403);
		assertThat(appel("GET", "/equipe", null, null).statusCode()).isEqualTo(401);
	}

}
