package com.example.gestionfinanciere.compte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.gestionfinanciere.compte.ResultatReinitialisation.Statut;
import com.example.gestionfinanciere.notification.EmailsRecus;
import com.example.gestionfinanciere.notification.EnvoiEmail;

class ReinitialisationServiceTest {

	private static final String EMAIL = "jean.dupont@cabinet-test.fr";
	private static final String NOUVEAU = "Nouveau#Pointeo2026";

	private final PasswordEncoder encodeur = new BCryptPasswordEncoder(4);
	private final CompteRepository comptes = mock(CompteRepository.class);
	private final JetonReinitialisationRepository jetons = mock(JetonReinitialisationRepository.class);
	private final SessionService sessions = mock(SessionService.class);
	private final EnvoiEmail emails = mock(EnvoiEmail.class);
	private final HorlogeReglable horloge = new HorlogeReglable(Instant.parse("2026-11-04T09:00:00Z"));
	private final Compte jean = new Compte(EMAIL, "ancienne-empreinte", Role.COLLABORATEUR, true, "Jean Dupont");
	private final Map<String, JetonReinitialisation> base = new HashMap<>();

	@BeforeEach
	void base() {
		when(comptes.findByEmail(EMAIL)).thenReturn(Optional.of(jean));
		when(jetons.save(any(JetonReinitialisation.class))).thenAnswer(appel -> {
			JetonReinitialisation jeton = appel.getArgument(0);
			base.put(jeton.getEmpreinteJeton(), jeton);
			return jeton;
		});
		when(jetons.findByEmpreinteJeton(anyString()))
				.thenAnswer(appel -> Optional.ofNullable(base.get(appel.<String>getArgument(0))));
	}

	private ReinitialisationService service() {
		return new ReinitialisationService(comptes, jetons, sessions, encodeur, horloge, emails,
				"https://frontend.test");
	}

	// Jeton lu dans le lien du dernier email reçu par Jean.
	private String demanderUnLien() {
		service().demander(EMAIL);
		return EmailsRecus.dernierJeton(emails, EMAIL);
	}

	@Test
	void rg1DemanderEnvoieUnLienValableUneHeure() {
		service().demander(" Jean.Dupont@Cabinet-Test.fr ");
		String jeton = EmailsRecus.dernierJeton(emails, EMAIL);
		verify(emails).envoyer(eq(EMAIL), anyString(),
				contains("https://frontend.test/reinitialiser-mot-de-passe?jeton=" + jeton));
		assertEquals(Instant.parse("2026-11-04T10:00:00Z"), base.get(Jetons.empreinte(jeton)).getExpireLe());
	}

	@Test
	void rg2EmailInconnuOuVideRienNEstEnvoye() {
		when(comptes.findByEmail("inconnu@test.fr")).thenReturn(Optional.empty());
		service().demander("inconnu@test.fr");
		service().demander("");
		verify(emails, never()).envoyer(anyString(), anyString(), anyString());
	}

	@Test
	void ca1NouveauMotDePasseValideChangeLeMotDePasseEtFermeLesSessions() {
		String jeton = demanderUnLien();
		assertTrue(service().lienValable(jeton));
		jean.enregistrerEchec(horloge.instant(), 1, Duration.ofMinutes(15));

		assertEquals(Statut.CHANGE, service().reinitialiser(jeton, NOUVEAU).statut());
		assertTrue(encodeur.matches(NOUVEAU, jean.getMotDePasseHash()));
		assertNull(jean.getBloqueJusqua());
		verify(sessions).fermerToutes(jean);
	}

	@Test
	void rg1LeLienNeSertQuUneFois() {
		String jeton = demanderUnLien();
		service().reinitialiser(jeton, NOUVEAU);
		assertFalse(service().lienValable(jeton));
		assertEquals(Statut.LIEN_EXPIRE, service().reinitialiser(jeton, NOUVEAU).statut());
	}

	@Test
	void ca2LienEnvoyeIlYADeuxHeuresExpire() {
		String jeton = demanderUnLien();
		horloge.avancer(Duration.ofHours(2));
		assertFalse(service().lienValable(jeton));
		assertEquals(Statut.LIEN_EXPIRE, service().reinitialiser(jeton, NOUVEAU).statut());
	}

	@Test
	void ca3MotDePasseTropFaibleRienNEstChange() {
		String jeton = demanderUnLien();
		ResultatReinitialisation resultat = service().reinitialiser(jeton, "bonjour");
		assertEquals(Statut.TROP_FAIBLE, resultat.statut());
		assertEquals(List.of(RegleMotDePasse.LONGUEUR, RegleMotDePasse.MAJUSCULE, RegleMotDePasse.CHIFFRE,
				RegleMotDePasse.SPECIAL), resultat.reglesNonRespectees());
		assertEquals("ancienne-empreinte", jean.getMotDePasseHash());
		assertTrue(service().lienValable(jeton));
	}

	@Test
	void lienInconnuOuVide() {
		assertFalse(service().lienValable(null));
		assertEquals(Statut.LIEN_EXPIRE, service().reinitialiser("inconnu", NOUVEAU).statut());
	}

}
