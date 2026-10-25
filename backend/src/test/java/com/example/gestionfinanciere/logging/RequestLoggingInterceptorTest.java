package com.example.gestionfinanciere.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class RequestLoggingInterceptorTest {

	private final RequestLoggingInterceptor interceptor = new RequestLoggingInterceptor();
	private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingInterceptor.class);
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	@AfterEach
	void detach() {
		logger.detachAppender(appender);
	}

	@Test
	void journaliseLaRouteDeclareeEtLeStatutSansExposerLesValeurs() {
		appender.start();
		logger.addAppender(appender);

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/items/42");
		request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/items/{id}");
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.setStatus(200);

		assertTrue(interceptor.preHandle(request, response, new Object()));
		interceptor.afterCompletion(request, response, new Object(), null);

		ILoggingEvent event = appender.list.get(0);
		assertEquals("controller", event.getMDCPropertyMap().get("layer"));
		assertEquals("/items/{id}", event.getMDCPropertyMap().get("route"));
		assertEquals("200", event.getMDCPropertyMap().get("status"));
	}

	@Test
	void journaliseUneRouteNonMatcheeCommeUnmatched() {
		appender.start();
		logger.addAppender(appender);

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/inconnu");
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.setStatus(404);

		interceptor.afterCompletion(request, response, new Object(), null);

		ILoggingEvent event = appender.list.get(0);
		assertEquals("unmatched", event.getMDCPropertyMap().get("route"));
	}

}
