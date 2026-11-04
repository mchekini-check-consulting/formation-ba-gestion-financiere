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
import com.example.gestionfinanciere.notification.EnvoiSms;

class CodeSmsServiceTest {

	private final PasswordEncoder encodeur = new BCryptPasswordEncoder(4);
	private final CodeSmsRepository codes = mock(CodeSmsRepository.class);
	private final EnvoiSms sms = mock(EnvoiSms.class);
	private final HorlogeReglable horloge = new HorlogeReglable(Instant.parse("2026-11-04T09:00:00Z"));
	private final Compte jean = new Compte("jean.dupont@cabinet-test.fr", "x", Role.COLLABORATEUR, true,
			"Jean Dupont", "0612345642");
	// Base simulée : codes enregistrés, retrouvés par empreinte du défi.
	private final Map<String, CodeSms> base = new HashMap<>();

	@BeforeEach
	void base() {
		when(codes.save(any(CodeSms.class))).thenAnswer(appel -> {
			CodeSms code = appel.getArgument(0);
			base.put(code.getEmpreinteDefi(), code);
			return code;
		});
		when(codes.findByEmpreinteDefi(anyString()))
				.thenAnswer(appel -> Optional.ofNullable(base.get(appel.<String>getArgument(0))));
	}

	private CodeSmsService service(boolean modeTest) {
		return new CodeSmsService(codes, encodeur, horloge, sms, modeTest);
	}

	@Test
	void rg1DemarrerEnvoieUnCodeASixChiffresAuNumeroDuCompte() {
		DefiSms defi = service(true).demarrer(jean);
		assertTrue(defi.codeDeTest().matches("\\d{6}"));
		assertEquals("06 •• •• •• 42", defi.telephoneMasque());
		verify(sms).envoyer(eq("0612345642"), contains(defi.codeDeTest()));
		CodeSms enregistre = base.get(Jetons.empreinte(defi.defi()));
		assertEquals(Instant.parse("2026-11-04T09:05:00Z"), enregistre.getExpireLe());
		assertNotEquals(defi.codeDeTest(), enregistre.getEmpreinteCode());
	}

	@Test
	void horsModeTestLeCodeNEstPasRenvoye() {
		assertNull(service(false).demarrer(jean).codeDeTest());
	}

	@Test
	void smsImpossibleRemonteLErreur() {
		doThrow(new EnvoiImpossibleException("opérateur", null)).when(sms).envoyer(anyString(), anyString());
		assertThrows(EnvoiImpossibleException.class, () -> service(true).demarrer(jean));
	}

	@Test
	void ca1BonCodeDansLesCinqMinutesOuvreLaConnexion() {
		CodeSmsService service = service(true);
		DefiSms defi = service.demarrer(jean);
		horloge.avancer(Duration.ofMinutes(4));
		ResultatCode resultat = service.verifier(defi.defi(), defi.codeDeTest());
		assertEquals(Statut.REUSSI, resultat.statut());
		assertSame(jean, resultat.compte());
	}

	@Test
	void rg1LeCodeNeSertQuUneFois() {
		CodeSmsService service = service(true);
		DefiSms defi = service.demarrer(jean);
		service.verifier(defi.defi(), defi.codeDeTest());
		assertEquals(Statut.DEMANDE_INVALIDE, service.verifier(defi.defi(), defi.codeDeTest()).statut());
	}

	@Test
	void ca2CodeFauxIndiqueLesTentativesRestantes() {
		CodeSmsService service = service(true);
		DefiSms defi = service.demarrer(jean);
		ResultatCode resultat = service.verifier(defi.defi(), autreCode(defi.codeDeTest()));
		assertEquals(Statut.FAUX, resultat.statut());
		assertEquals(2, resultat.tentativesRestantes());
		assertEquals(1, service.verifier(defi.defi(), "abc").tentativesRestantes());
	}

	@Test
	void rg3TroisCodesFauxRenvoientALaConnexion() {
		CodeSmsService service = service(true);
		DefiSms defi = service.demarrer(jean);
		String faux = autreCode(defi.codeDeTest());
		service.verifier(defi.defi(), faux);
		service.verifier(defi.defi(), faux);
		assertEquals(Statut.TROP_DE_CODES_FAUX, service.verifier(defi.defi(), faux).statut());
		assertEquals(Statut.DEMANDE_INVALIDE, service.verifier(defi.defi(), defi.codeDeTest()).statut());
	}

	@Test
	void ca3CodeRecuIlYASixMinutesExpire() {
		CodeSmsService service = service(true);
		DefiSms defi = service.demarrer(jean);
		horloge.avancer(Duration.ofMinutes(6));
		ResultatCode resultat = service.verifier(defi.defi(), defi.codeDeTest());
		assertEquals(ResultatCode.Statut.EXPIRE, resultat.statut());
		assertEquals(3, resultat.tentativesRestantes());
	}

	@Test
	void defiInconnuOuVide() {
		CodeSmsService service = service(true);
		assertEquals(Statut.DEMANDE_INVALIDE, service.verifier("inconnu", "123456").statut());
		assertEquals(Statut.DEMANDE_INVALIDE, service.verifier(null, "123456").statut());
		assertEquals(ResultatRenvoi.Statut.DEMANDE_INVALIDE, service.renvoyer(" ").statut());
	}

	@Test
	void rg2RenvoiAvant60SecondesRefuseAvecLeTempsRestant() {
		CodeSmsService service = service(true);
		DefiSms defi = service.demarrer(jean);
		horloge.avancer(Duration.ofSeconds(20));
		ResultatRenvoi resultat = service.renvoyer(defi.defi());
		assertEquals(ResultatRenvoi.Statut.TROP_TOT, resultat.statut());
		assertEquals(40, resultat.secondesRestantes());
	}

	@Test
	void rg2LeNouveauCodeRemplaceLAncien() {
		CodeSmsService service = service(true);
		DefiSms defi = service.demarrer(jean);
		horloge.avancer(Duration.ofSeconds(60));
		ResultatRenvoi renvoi = service.renvoyer(defi.defi());
		assertEquals(ResultatRenvoi.Statut.RENVOYE, renvoi.statut());
		assertEquals("06 •• •• •• 42", renvoi.telephoneMasque());
		verify(sms, times(2)).envoyer(eq("0612345642"), anyString());
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
