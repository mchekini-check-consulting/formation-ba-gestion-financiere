package com.example.gestionfinanciere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.CompteRepository;
import com.example.gestionfinanciere.compte.HorlogeReglable;
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.notification.EnvoiEmail;
import com.example.gestionfinanciere.notification.EnvoiImpossibleException;
import com.example.gestionfinanciere.notification.EmailsRecus;

// US-04 code reçu par email, US-05 blocage, US-06 mot de passe oublié, US-07 session : vrai serveur HTTP,
// migrations Flyway et PostgreSQL 16 jetable. L'horloge est avancée à la main (15 min, 5 min, 1 h,
// 30 min) ; le mode test renvoie le code et le lien. Le service d'envoi d'emails est remplacé
// par un bouchon (mock), qui permet aussi de simuler un envoi impossible.
// Chaque test crée son propre compte : aucun test ne dépend d'un autre.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthentificationIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	private static final String MOT_DE_PASSE = "Pointeo2026!";
	private static final String NOUVEAU = "Nouveau#Pointeo2026";

	@TestConfiguration
	static class HorlogeDeTest {

		@Bean
		@Primary
		HorlogeReglable horlogeReglable() {
			return new HorlogeReglable(Instant.parse("2026-11-04T09:00:00Z"));
		}

	}

	@MockitoBean
	private EnvoiEmail envoiEmail;

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private HorlogeReglable horloge;

	@Autowired
	private CompteRepository comptes;

	@Autowired
	private PasswordEncoder encodeur;

	private final HttpClient http = HttpClient.newHttpClient();

	// --- outils -----------------------------------------------------------------------

	private String nouveauCompte(Role role) {
		String email = "it-" + UUID.randomUUID() + "@cabinet-test.fr";
		comptes.save(new Compte(email, encodeur.encode(MOT_DE_PASSE), role, true, "Compte de test"));
		return email;
	}

	private HttpResponse<String> post(String chemin, String json, String jetonSession)
			throws IOException, InterruptedException {
		HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json));
		if (jetonSession != null) {
			requete.header("Authorization", "Bearer " + jetonSession);
		}
		return http.send(requete.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> post(String chemin, String json) throws IOException, InterruptedException {
		return post(chemin, json, null);
	}

	private HttpResponse<String> session(String jeton) throws IOException, InterruptedException {
		HttpRequest requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/auth/session"))
				.header("Authorization", "Bearer " + jeton).GET().build();
		return http.send(requete, HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> connexion(String email, String motDePasse) throws IOException, InterruptedException {
		return post("/auth/connexion", "{\"email\":\"" + email + "\",\"motDePasse\":\"" + motDePasse + "\"}");
	}

	private HttpResponse<String> code(String defi, String code) throws IOException, InterruptedException {
		return post("/auth/code", "{\"defi\":\"" + defi + "\",\"code\":\"" + code + "\"}");
	}

	private static String champ(HttpResponse<String> reponse, String nom) {
		Matcher m = Pattern.compile("\"" + nom + "\":\"([^\"]*)\"").matcher(reponse.body());
		assertThat(m.find()).as("champ %s dans %s", nom, reponse.body()).isTrue();
		return m.group(1);
	}

	// Connexion complète (mot de passe + code reçu par email) : renvoie le jeton de session.
	private String seConnecter(String email, String motDePasse) throws IOException, InterruptedException {
		HttpResponse<String> etape1 = connexion(email, motDePasse);
		assertThat(etape1.statusCode()).isEqualTo(200);
		HttpResponse<String> etape2 = code(champ(etape1, "defi"), EmailsRecus.dernierCode(envoiEmail));
		assertThat(etape2.statusCode()).isEqualTo(200);
		return champ(etape2, "jeton");
	}

	private static String autreCode(String code) {
		return "000000".equals(code) ? "111111" : "000000";
	}

	// --- US-04 Code reçu par email ------------------------------------------------------

	@Test
	void us04Ca1BonCodeOuvreLaSessionDuCollaborateur() throws Exception {
		HttpResponse<String> etape1 = connexion("jean.dupont@cabinet-test.fr", MOT_DE_PASSE);
		assertThat(champ(etape1, "emailMasque")).isEqualTo("j•••••@cabinet-test.fr");
		String recu = EmailsRecus.dernierCode(envoiEmail);
		verify(envoiEmail).envoyer(eq("jean.dupont@cabinet-test.fr"), anyString(), contains(recu));
		assertThat(etape1.body()).doesNotContain(recu);
		HttpResponse<String> etape2 = code(champ(etape1, "defi"), recu);
		assertThat(etape2.statusCode()).isEqualTo(200);
		assertThat(etape2.body()).contains("\"role\":\"COLLABORATEUR\"").contains("\"nomAffiche\":\"Jean Dupont\"");

		HttpResponse<String> moi = session(champ(etape2, "jeton"));
		assertThat(moi.statusCode()).isEqualTo(200);
		assertThat(moi.body()).contains("Jean Dupont");
	}

	@Test
	void us04EmailImpossibleAfficheLeMessage() throws Exception {
		String email = nouveauCompte(Role.COLLABORATEUR);
		doThrow(new EnvoiImpossibleException("api", null)).when(envoiEmail).envoyer(eq(email), anyString(),
				anyString());
		HttpResponse<String> reponse = connexion(email, MOT_DE_PASSE);
		assertThat(reponse.statusCode()).isEqualTo(503);
		assertThat(champ(reponse, "message")).isEqualTo("L’email n’a pas pu être envoyé. Réessayez.");
	}

	@Test
	void us04Ca2CodeFauxPuisRg3RetourALaConnexion() throws Exception {
		HttpResponse<String> etape1 = connexion(nouveauCompte(Role.DIRIGEANT), MOT_DE_PASSE);
		String defi = champ(etape1, "defi");
		String faux = autreCode(EmailsRecus.dernierCode(envoiEmail));

		HttpResponse<String> premier = code(defi, faux);
		assertThat(premier.statusCode()).isEqualTo(401);
		assertThat(champ(premier, "message")).isEqualTo("Code incorrect. Il vous reste 2 tentatives.");
		code(defi, faux);
		HttpResponse<String> troisieme = code(defi, faux);
		assertThat(troisieme.body()).contains("\"retourConnexion\":true");
		assertThat(code(defi, EmailsRecus.dernierCode(envoiEmail)).statusCode()).isEqualTo(401);
	}

	@Test
	void us04Ca3CodeRecuIlYASixMinutes() throws Exception {
		HttpResponse<String> etape1 = connexion(nouveauCompte(Role.COLLABORATEUR), MOT_DE_PASSE);
		horloge.avancer(Duration.ofMinutes(6));
		HttpResponse<String> reponse = code(champ(etape1, "defi"), EmailsRecus.dernierCode(envoiEmail));
		assertThat(reponse.statusCode()).isEqualTo(410);
		assertThat(champ(reponse, "message")).isEqualTo("Ce code a expiré. Demandez un nouveau code.");
	}

	@Test
	void us04Rg2RenvoyerUnCodeApres60Secondes() throws Exception {
		HttpResponse<String> etape1 = connexion(nouveauCompte(Role.COLLABORATEUR), MOT_DE_PASSE);
		String defi = champ(etape1, "defi");
		assertThat(post("/auth/code/renvoyer", "{\"defi\":\"" + defi + "\"}").statusCode()).isEqualTo(429);
		horloge.avancer(Duration.ofSeconds(61));
		HttpResponse<String> renvoi = post("/auth/code/renvoyer", "{\"defi\":\"" + defi + "\"}");
		assertThat(renvoi.statusCode()).isEqualTo(200);
		assertThat(code(defi, EmailsRecus.dernierCode(envoiEmail)).statusCode()).isEqualTo(200);
	}

	// --- US-05 Blocage après 3 échecs --------------------------------------------------

	@Test
	void us05Ca1BlocageAuTroisiemeEchecPuisCa2FinDuBlocage() throws Exception {
		String email = nouveauCompte(Role.COLLABORATEUR);
		assertThat(connexion(email, "mauvais-1").statusCode()).isEqualTo(401);
		assertThat(connexion(email, "mauvais-2").statusCode()).isEqualTo(401);
		HttpResponse<String> troisieme = connexion(email, "mauvais-3");
		assertThat(troisieme.statusCode()).isEqualTo(423);
		assertThat(champ(troisieme, "message")).startsWith("Compte bloqué 15 minutes");

		assertThat(connexion(email, MOT_DE_PASSE).statusCode()).as("bloqué même avec le bon mot de passe")
				.isEqualTo(423);
		horloge.avancer(Duration.ofMinutes(16));
		assertThat(connexion(email, MOT_DE_PASSE).statusCode()).isEqualTo(200);
	}

	@Test
	void us05Rg3UneConnexionReussieRemetLeCompteurAZero() throws Exception {
		String email = nouveauCompte(Role.COLLABORATEUR);
		connexion(email, "mauvais-1");
		connexion(email, "mauvais-2");
		assertThat(connexion(email, MOT_DE_PASSE).statusCode()).isEqualTo(200);
		assertThat(connexion(email, "mauvais-3").statusCode()).isEqualTo(401);
	}

	// --- US-06 Mot de passe oublié -----------------------------------------------------

	private String demanderUnLien(String email) throws IOException, InterruptedException {
		HttpResponse<String> reponse = post("/auth/mot-de-passe-oublie", "{\"email\":\"" + email + "\"}");
		assertThat(reponse.statusCode()).isEqualTo(200);
		assertThat(champ(reponse, "message")).isEqualTo("Si un compte existe, un email vous a été envoyé");
		return EmailsRecus.dernierJeton(envoiEmail);
	}

	private HttpResponse<String> reinitialiser(String jeton, String motDePasse)
			throws IOException, InterruptedException {
		return post("/auth/reinitialisation", "{\"jeton\":\"" + jeton + "\",\"motDePasse\":\"" + motDePasse + "\"}");
	}

	@Test
	void us06Ca1NouveauMotDePasseEtConnexion() throws Exception {
		String email = nouveauCompte(Role.DIRIGEANT);
		String sessionOuverte = seConnecter(email, MOT_DE_PASSE);
		String jeton = demanderUnLien(email);
		assertThat(post("/auth/reinitialisation/verifier", "{\"jeton\":\"" + jeton + "\"}").statusCode())
				.isEqualTo(204);

		assertThat(reinitialiser(jeton, NOUVEAU).statusCode()).isEqualTo(204);
		assertThat(connexion(email, MOT_DE_PASSE).statusCode()).isEqualTo(401);
		assertThat(seConnecter(email, NOUVEAU)).isNotBlank();
		assertThat(session(sessionOuverte).statusCode()).as("les autres sessions sont fermées").isEqualTo(401);
		assertThat(reinitialiser(jeton, NOUVEAU).statusCode()).as("lien utilisable une seule fois").isEqualTo(410);
	}

	@Test
	void us06Ca2LienEnvoyeIlYADeuxHeures() throws Exception {
		String jeton = demanderUnLien(nouveauCompte(Role.COLLABORATEUR));
		horloge.avancer(Duration.ofHours(2));
		HttpResponse<String> reponse = post("/auth/reinitialisation/verifier", "{\"jeton\":\"" + jeton + "\"}");
		assertThat(reponse.statusCode()).isEqualTo(410);
		assertThat(champ(reponse, "message")).isEqualTo("Ce lien a expiré, demandez-en un nouveau");
	}

	@Test
	void us06Ca3MotDePasseTropFaibleRienNEstChange() throws Exception {
		String email = nouveauCompte(Role.COLLABORATEUR);
		HttpResponse<String> reponse = reinitialiser(demanderUnLien(email), "bonjour");
		assertThat(reponse.statusCode()).isEqualTo(400);
		assertThat(reponse.body()).contains("12 caractères minimum", "Une majuscule", "Un chiffre",
				"Un caractère spécial");
		assertThat(connexion(email, MOT_DE_PASSE).statusCode()).isEqualTo(200);
	}

	@Test
	void us06Rg2EmailInconnuMemeMessage() throws Exception {
		HttpResponse<String> reponse = post("/auth/mot-de-passe-oublie", "{\"email\":\"personne@cabinet-test.fr\"}");
		assertThat(reponse.statusCode()).isEqualTo(200);
		assertThat(champ(reponse, "message")).isEqualTo("Si un compte existe, un email vous a été envoyé");
	}

	@Test
	void us06LeChangementDeMotDePasseDebloqueLeCompte() throws Exception {
		String email = nouveauCompte(Role.COLLABORATEUR);
		connexion(email, "mauvais-1");
		connexion(email, "mauvais-2");
		assertThat(connexion(email, "mauvais-3").statusCode()).isEqualTo(423);
		reinitialiser(demanderUnLien(email), NOUVEAU);
		assertThat(connexion(email, NOUVEAU).statusCode()).isEqualTo(200);
	}

	// --- US-07 Déconnexion et expiration -----------------------------------------------

	@Test
	void us07Ca1DeconnexionFermeLaSession() throws Exception {
		String jeton = seConnecter(nouveauCompte(Role.COLLABORATEUR), MOT_DE_PASSE);
		assertThat(post("/auth/deconnexion", "{}", jeton).statusCode()).isEqualTo(204);
		HttpResponse<String> apres = session(jeton);
		assertThat(apres.statusCode()).isEqualTo(401);
		assertThat(champ(apres, "code")).isEqualTo("NON_CONNECTE");
	}

	@Test
	void us07Ca2InactifDepuisTrenteMinutes() throws Exception {
		String jeton = seConnecter(nouveauCompte(Role.COLLABORATEUR), MOT_DE_PASSE);
		horloge.avancer(Duration.ofMinutes(30));
		HttpResponse<String> reponse = session(jeton);
		assertThat(reponse.statusCode()).isEqualTo(401);
		assertThat(champ(reponse, "message")).isEqualTo("Votre session a expiré");
		assertThat(champ(reponse, "code")).isEqualTo("SESSION_EXPIREE");
	}

	@Test
	void us07LActiviteRepousseLExpiration() throws Exception {
		String jeton = seConnecter(nouveauCompte(Role.COLLABORATEUR), MOT_DE_PASSE);
		horloge.avancer(Duration.ofMinutes(20));
		assertThat(session(jeton).statusCode()).isEqualTo(200);
		horloge.avancer(Duration.ofMinutes(20));
		assertThat(session(jeton).statusCode()).isEqualTo(200);
	}

}
