package com.example.gestionfinanciere.compte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.gestionfinanciere.compte.ResultatCode.Statut;
import com.example.gestionfinanciere.notification.EnvoiImpossibleException;
import com.example.gestionfinanciere.notification.EnvoiEmail;

// US-04 : le service d'envoi d'emails est remplacé par un bouchon (mock), y compris pour l'échec d'envoi.
class CodeConnexionServiceTest {

	private final PasswordEncoder encodeur = new BCryptPasswordEncoder(4);
	private final CodeConnexionRepository codes = mock(CodeConnexionRepository.class);
	private final EnvoiEmail email = mock(EnvoiEmail.class);
	private final HorlogeReglable horloge = new HorlogeReglable(Instant.parse("2026-11-04T09:00:00Z"));
	private final Compte jean = new Compte("jean.dupont@cabinet-test.fr", "x", Role.COLLABORATEUR, true,
			"Jean Dupont");
	// Base simulée : codes enregistrés, retrouvés par empreinte du défi.
	private final Map<String, CodeConnexion> base = new HashMap<>();

	@BeforeEach
	void base() {
		when(codes.save(any(CodeConnexion.class))).thenAnswer(appel -> {
			CodeConnexion code = appel.getArgument(0);
			base.put(code.getEmpreinteDefi(), code);
			return code;
		});
		when(codes.findByEmpreinteDefi(anyString()))
				.thenAnswer(appel -> Optional.ofNullable(base.get(appel.<String>getArgument(0))));
	}

	private CodeConnexionService service(boolean modeTest) {
		return new CodeConnexionService(codes, encodeur, horloge, email, modeTest);
	}

	@Test
	void rg1DemarrerEnvoieUnCodeASixChiffresALEmailDuCompte() {
		DefiCode defi = service(true).demarrer(jean);
		assertTrue(defi.codeDeTest().matches("\\d{6}"));
		assertEquals("j•••••@cabinet-test.fr", defi.emailMasque());
		verify(email).envoyer(eq("jean.dupont@cabinet-test.fr"), eq(CodeConnexionService.SUJET),
				contains(defi.codeDeTest()));
		CodeConnexion enregistre = base.get(Jetons.empreinte(defi.defi()));
		assertEquals(Instant.parse("2026-11-04T09:05:00Z"), enregistre.getExpireLe());
		assertNotEquals(defi.codeDeTest(), enregistre.getEmpreinteCode());
	}

	@Test
	void horsModeTestLeCodeNEstPasRenvoye() {
		assertNull(service(false).demarrer(jean).codeDeTest());
	}

	@Test
	void emailImpossibleRemonteLErreur() {
		doThrow(new EnvoiImpossibleException("api", null)).when(email).envoyer(anyString(), anyString(), anyString());
		assertThrows(EnvoiImpossibleException.class, () -> service(true).demarrer(jean));
	}

	@Test
	void ca1BonCodeDansLesCinqMinutesOuvreLaConnexion() {
		CodeConnexionService service = service(true);
		DefiCode defi = service.demarrer(jean);
		horloge.avancer(Duration.ofMinutes(4));
		ResultatCode resultat = service.verifier(defi.defi(), defi.codeDeTest());
		assertEquals(Statut.REUSSI, resultat.statut());
		assertSame(jean, resultat.compte());
	}

	@Test
	void rg1LeCodeNeSertQuUneFois() {
		CodeConnexionService service = service(true);
		DefiCode defi = service.demarrer(jean);
		service.verifier(defi.defi(), defi.codeDeTest());
		assertEquals(Statut.DEMANDE_INVALIDE, service.verifier(defi.defi(), defi.codeDeTest()).statut());
	}

	@Test
	void ca2CodeFauxIndiqueLesTentativesRestantes() {
		CodeConnexionService service = service(true);
		DefiCode defi = service.demarrer(jean);
		ResultatCode resultat = service.verifier(defi.defi(), autreCode(defi.codeDeTest()));
		assertEquals(Statut.FAUX, resultat.statut());
		assertEquals(2, resultat.tentativesRestantes());
		assertEquals(1, service.verifier(defi.defi(), "abc").tentativesRestantes());
	}

	@Test
	void rg3TroisCodesFauxRenvoientALaConnexion() {
		CodeConnexionService service = service(true);
		DefiCode defi = service.demarrer(jean);
		String faux = autreCode(defi.codeDeTest());
		service.verifier(defi.defi(), faux);
		service.verifier(defi.defi(), faux);
		assertEquals(Statut.TROP_DE_CODES_FAUX, service.verifier(defi.defi(), faux).statut());
		assertEquals(Statut.DEMANDE_INVALIDE, service.verifier(defi.defi(), defi.codeDeTest()).statut());
	}

	@Test
	void ca3CodeRecuIlYASixMinutesExpire() {
		CodeConnexionService service = service(true);
		DefiCode defi = service.demarrer(jean);
		horloge.avancer(Duration.ofMinutes(6));
		ResultatCode resultat = service.verifier(defi.defi(), defi.codeDeTest());
		assertEquals(ResultatCode.Statut.EXPIRE, resultat.statut());
		assertEquals(3, resultat.tentativesRestantes());
	}

	@Test
	void defiInconnuOuVide() {
		CodeConnexionService service = service(true);
		assertEquals(Statut.DEMANDE_INVALIDE, service.verifier("inconnu", "123456").statut());
		assertEquals(Statut.DEMANDE_INVALIDE, service.verifier(null, "123456").statut());
		assertEquals(ResultatRenvoi.Statut.DEMANDE_INVALIDE, service.renvoyer(" ").statut());
	}

	@Test
	void rg2RenvoiAvant60SecondesRefuseAvecLeTempsRestant() {
		CodeConnexionService service = service(true);
		DefiCode defi = service.demarrer(jean);
		horloge.avancer(Duration.ofSeconds(20));
		ResultatRenvoi resultat = service.renvoyer(defi.defi());
		assertEquals(ResultatRenvoi.Statut.TROP_TOT, resultat.statut());
		assertEquals(40, resultat.secondesRestantes());
	}

	@Test
	void rg2LeNouveauCodeRemplaceLAncien() {
		CodeConnexionService service = service(true);
		DefiCode defi = service.demarrer(jean);
		horloge.avancer(Duration.ofSeconds(60));
		ResultatRenvoi renvoi = service.renvoyer(defi.defi());
		assertEquals(ResultatRenvoi.Statut.RENVOYE, renvoi.statut());
		verify(email, times(2)).envoyer(eq("jean.dupont@cabinet-test.fr"), anyString(), anyString());
		if (!renvoi.codeDeTest().equals(defi.codeDeTest())) {
			assertEquals(Statut.FAUX, service.verifier(defi.defi(), defi.codeDeTest()).statut());
		}
		horloge.avancer(Duration.ofMinutes(4));
		assertEquals(Statut.REUSSI, service.verifier(defi.defi(), renvoi.codeDeTest()).statut());
	}

	private static String autreCode(String code) {
		return "000000".equals(code) ? "111111" : "000000";
	}

}
