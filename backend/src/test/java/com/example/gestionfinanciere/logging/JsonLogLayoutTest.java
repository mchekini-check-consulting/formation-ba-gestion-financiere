package com.example.gestionfinanciere.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class JsonLogLayoutTest {

	private final JsonLogLayout layout = new JsonLogLayout();
	private final ObjectMapper mapper = new ObjectMapper();
	private final Logger logger = (Logger) LoggerFactory.getLogger("json-log-layout-test");
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	@AfterEach
	void detach() {
		logger.detachAppender(appender);
		MDC.clear();
	}

	@Test
	void produitUneLigneJsonAvecLesChampsMinimauxEtLeContexteMdc() throws Exception {
		appender.start();
		logger.addAppender(appender);

		MDC.put("layer", "service");
		logger.info("message de test");

		ILoggingEvent event = appender.list.get(0);
		Map<String, Object> json = mapper.readValue(layout.doLayout(event), new TypeReference<>() {
		});

		assertEquals("info", json.get("level"));
		assertEquals("message de test", json.get("msg"));
		assertEquals("service", json.get("layer"));
		assertNotNull(json.get("time"));
	}

	@Test
	void inclutLErreurQuandUneExceptionEstJournalisee() throws Exception {
		appender.start();
		logger.addAppender(appender);

		logger.error("échec", new IllegalStateException("boum"));

		ILoggingEvent event = appender.list.get(0);
		Map<String, Object> json = mapper.readValue(layout.doLayout(event), new TypeReference<>() {
		});

		assertEquals("java.lang.IllegalStateException: boum", json.get("err"));
	}

}
