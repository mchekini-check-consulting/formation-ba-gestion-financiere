package com.example.gestionfinanciere.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class EvenementMetierTest {

	private enum Statut {
		CABINET_CREE
	}

	private final Logger logger = (Logger) LoggerFactory.getLogger(EvenementMetier.class);
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	@BeforeEach
	void attacher() {
		appender.start();
		logger.addAppender(appender);
	}

	@AfterEach
	void detacher() {
		logger.detachAppender(appender);
	}

	private ILoggingEvent dernier() {
		return appender.list.get(appender.list.size() - 1);
	}

	@Test
	void ecritLActionSonResultatEtLesIdentifiants() {
		Map<String, Object> identifiants = new LinkedHashMap<>();
		identifiants.put("compteId", 34L);
		identifiants.put("cabinetId", null);
		EvenementMetier.de("inscription.code_verifie", Statut.CABINET_CREE).avec(identifiants)
				.avec("tentativesRestantes", 2).info();

		ILoggingEvent event = dernier();
		Map<String, String> mdc = event.getMDCPropertyMap();
		assertEquals(Level.INFO, event.getLevel());
		assertEquals("inscription.code_verifie : cabinet_cree", event.getFormattedMessage());
		assertEquals("service", mdc.get("layer"));
		assertEquals("inscription.code_verifie", mdc.get("event"));
		assertEquals("cabinet_cree", mdc.get("outcome"));
		assertEquals("34", mdc.get("compteId"));
		assertEquals("2", mdc.get("tentativesRestantes"));
		assertFalse(mdc.containsKey("cabinetId"), "une valeur absente n'est pas écrite");
	}

	@Test
	void listeLesNomsDesChampsRefusesSansLeursValeurs() {
		EvenementMetier.de("dossier.creation", "invalide")
				.avecChampsInvalides(Map.of("siren", "Le SIREN comporte 9 chiffres", "raisonSociale", "x")).warn();
		assertEquals("raisonSociale,siren", dernier().getMDCPropertyMap().get("champsInvalides"));
		assertEquals(Level.WARN, dernier().getLevel());

		EvenementMetier.de("dossier.creation", "cree").avecChampsInvalides(Map.of()).info();
		assertFalse(dernier().getMDCPropertyMap().containsKey("champsInvalides"));
	}

	@Test
	void niveauSelonLeResultatEtCoucheControleur() {
		EvenementMetier.de("session.fermee", "deconnexion").journaliser(true);
		assertEquals(Level.INFO, dernier().getLevel());
		EvenementMetier.de("session.fermee", "session_inconnue").journaliser(false);
		assertEquals(Level.WARN, dernier().getLevel());
		EvenementMetier.de("equipe.invitation", "email_impossible").error();
		assertEquals(Level.ERROR, dernier().getLevel());
		EvenementMetier.de("equipe.acces", "refuse").depuisLeControleur().warn();
		assertEquals("controller", dernier().getMDCPropertyMap().get("layer"));
	}

	@Test
	void neLaissePasSesChampsDansLeContexteDesLogsSuivants() {
		EvenementMetier.de("dossier.consultation", "trouve").avec("dossierId", 7).info();
		assertNull(MDC.get("event"));
		assertNull(MDC.get("dossierId"));
	}

}
