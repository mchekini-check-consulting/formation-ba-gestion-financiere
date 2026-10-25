package com.example.gestionfinanciere.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcContextTest {

	@AfterEach
	void clear() {
		MDC.clear();
	}

	@Test
	void retireLaCleALaFermetureSiAbsenteAvant() {
		try (MdcContext ctx = MdcContext.open().with("layer", "db")) {
			assertEquals("db", MDC.get("layer"));
		}
		assertNull(MDC.get("layer"));
	}

	@Test
	void restaureLaValeurEnglobanteALaFermeture() {
		MDC.put("layer", "service");
		try (MdcContext ctx = MdcContext.open().with("layer", "db")) {
			assertEquals("db", MDC.get("layer"));
		}
		assertEquals("service", MDC.get("layer"));
	}

}
