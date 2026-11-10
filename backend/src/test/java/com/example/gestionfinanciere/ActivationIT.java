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
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.equipe.Invitation;
import com.example.gestionfinanciere.equipe.InvitationRepository;
import com.example.gestionfinanciere.notification.EmailsRecus;
import com.example.gestionfinanciere.notification.EnvoiEmail;

// US-08 Activer son compte : vrai serveur HTTP, migrations Flyway et PostgreSQL 16 jetable ; les liens
// d'invitation US-14 et US-19 sont lus dans l'email envoyé (simulé).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ActivationIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	private static final String MOT_DE_PASSE = "Sophie#Pointeo2026";
	private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");
	private static final Pattern SESSION = Pattern.compile("\"jeton\":\"([^\"]+)\"");

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

	// Invitation envoyée : jeton lu dans le lien de l'email, jamais dans la réponse.
	private String lienRecu(String reponseInvitation) {
		assertThat(reponseInvitation).contains("INVITE").doesNotContain("jeton");
		return EmailsRecus.dernierJeton(envoiEmail);
	}

	private static String extraire(Pattern motif, String texte) {
		Matcher trouve = motif.matcher(texte);
		assertThat(trouve.find()).isTrue();
		return trouve.group(1);
	}

	private HttpResponse<String> verifier(String jeton) throws IOException, InterruptedException {
		return appel("POST", "/auth/activation/verifier", "{\"jeton\":\"" + jeton + "\"}", null);
	}

	private HttpResponse<String> activer(String jeton, String motDePasse, boolean cgu)
			throws IOException, InterruptedException {
		return appel("POST", "/auth/activation", """
				{"jeton":"%s","motDePasse":"%s","cguAcceptees":%s}""".formatted(jeton, motDePasse, cgu), null);
	}

	// Parcours : dossier créé, dirigeant invité (US-14), activation depuis le lien, espace client.
	@Test
	void dirigeantActiveSonEspace() throws Exception {
		String jean = jeanConnecte();
		Long dossier = Long.valueOf(extraire(ID, appel("POST", "/dossiers",
				"{\"raisonSociale\":\"Boulangerie Leroy SARL\",\"siren\":\"812345678\"}", jean).body()));
		String email = "sophie+" + dossier + "@boulangerie-test.fr";
		String lien = lienRecu(appel("POST", "/dossiers/" + dossier + "/invitation",
				"{\"prenom\":\"Sophie\",\"nom\":\"Martin\",\"email\":\"" + email + "\"}", jean).body());

		HttpResponse<String> valable = verifier(lien);
		assertThat(valable.statusCode()).isEqualTo(200);
		assertThat(valable.body()).contains("\"nomAffiche\":\"Sophie Martin\"", "\"role\":\"DIRIGEANT\"");

		// RG2 : CGU obligatoires, règles du mot de passe.
		HttpResponse<String> sansCgu = activer(lien, MOT_DE_PASSE, false);
		assertThat(sansCgu.statusCode()).isEqualTo(400);
		assertThat(sansCgu.body()).contains("Vous devez accepter les conditions d'utilisation");
		HttpResponse<String> faible = activer(lien, "court", true);
		assertThat(faible.statusCode()).isEqualTo(400);
		assertThat(faible.body()).contains("Le mot de passe ne respecte pas toutes les règles", "12 caractères minimum");

		// RG3 : actif aussitôt, sans code ; session ouverte sur l'espace client.
		HttpResponse<String> active = activer(lien, MOT_DE_PASSE, true);
		assertThat(active.statusCode()).isEqualTo(200);
		assertThat(active.body()).contains("\"role\":\"DIRIGEANT\"", "\"nomAffiche\":\"Sophie Martin\"");
		String session = extraire(SESSION, active.body());
		assertThat(appel("GET", "/auth/session", null, session).body()).contains("\"role\":\"DIRIGEANT\"");
		assertThat(comptes.findByEmail(email).orElseThrow().isActif()).isTrue();
		assertThat(appel("GET", "/dossiers/" + dossier, null, jean).body()).contains("\"etatEspaceClient\":\"ACTIF\"");

		// Contrôle d'accès : la dirigeante n'a pas accès aux dossiers du cabinet.
		assertThat(appel("GET", "/dossiers", null, session).statusCode()).isEqualTo(403);

		// RG1 : une seule fois.
		HttpResponse<String> deja = verifier(lien);
		assertThat(deja.statusCode()).isEqualTo(409);
		assertThat(deja.body()).contains("Votre espace est déjà activé");
		assertThat(activer(lien, "Autre#Pointeo2026", true).statusCode()).isEqualTo(409);

		// Elle se connecte ensuite avec le mot de passe choisi.
		HttpResponse<String> connexion = appel("POST", "/auth/connexion",
				"{\"email\":\"" + email + "\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}", null);
		assertThat(connexion.statusCode()).isEqualTo(200);
	}

	// US-19 : collaborateur invité par l'administrateur.
	@Test
	void collaborateurActiveSonCompte() throws Exception {
		String jean = jeanConnecte();
		String email = "lucie+" + UUID.randomUUID() + "@cabinet-test.fr";
		String lien = lienRecu(appel("POST", "/equipe/invitations", """
				{"prenom":"Lucie","nom":"Moreau","email":"%s","role":"COMPTABLE"}""".formatted(email), jean).body());
		HttpResponse<String> active = activer(lien, MOT_DE_PASSE, true);
		assertThat(active.statusCode()).isEqualTo(200);
		assertThat(active.body()).contains("\"role\":\"COLLABORATEUR\"", "\"administrateur\":false");
		assertThat(appel("GET", "/dossiers", null, extraire(SESSION, active.body())).statusCode()).isEqualTo(200);
	}

	// RG1 : lien expiré (plus de 7 jours), annulé par un renvoi, ou inconnu.
	@Test
	void lienExpireAnnuleOuInconnu() throws Exception {
		String id = UUID.randomUUID().toString();
		Compte invite = comptes.save(Compte.dirigeantInvite(null, "vieux+" + id + "@test.fr", "x", "Paul Vieux"));
		Compte jean = comptes.save(new Compte("jean+" + id + "@test.fr", "x", Role.COLLABORATEUR, true, "Jean"));
		Instant envoi = Instant.now().minus(Duration.ofDays(8));
		invitations.save(new Invitation(invite, jean, Jetons.empreinte("expire-" + id), envoi,
				envoi.plus(Duration.ofDays(7))));
		Invitation annulee = new Invitation(invite, jean, Jetons.empreinte("annule-" + id), Instant.now(),
				Instant.now().plus(Duration.ofDays(7)));
		annulee.annuler(Instant.now());
		invitations.save(annulee);

		for (String jeton : new String[] { "expire-" + id, "annule-" + id, "inconnu-" + id }) {
			HttpResponse<String> verification = verifier(jeton);
			assertThat(verification.statusCode()).isEqualTo(410);
			assertThat(verification.body())
					.contains("Cette invitation a expiré. Demandez à votre cabinet de vous la renvoyer.");
			assertThat(activer(jeton, MOT_DE_PASSE, true).statusCode()).isEqualTo(410);
		}
		assertThat(comptes.findById(invite.getId()).orElseThrow().isActif()).isFalse();
	}

}
