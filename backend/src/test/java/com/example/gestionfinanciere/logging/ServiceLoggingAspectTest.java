package com.example.gestionfinanciere.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class ServiceLoggingAspectTest {

	private final ServiceLoggingAspect aspect = new ServiceLoggingAspect();

	@AfterEach
	void clear() {
		MDC.clear();
	}

	@Test
	void renvoieLeResultatEtNettoieLeMdcEnCasDeSucces() throws Throwable {
		ProceedingJoinPoint joinPoint = joinPointFor("ItemService.lister()");
		when(joinPoint.proceed()).thenReturn("ok");

		Object result = aspect.logServiceCall(joinPoint);

		assertEquals("ok", result);
		assertNull(MDC.get("layer"));
		assertNull(MDC.get("method"));
		assertNull(MDC.get("durationMs"));
	}

	@Test
	void propageLExceptionEtNettoieLeMdc() throws Throwable {
		ProceedingJoinPoint joinPoint = joinPointFor("ItemService.creer(..)");
		RuntimeException boom = new RuntimeException("boom");
		when(joinPoint.proceed()).thenThrow(boom);

		RuntimeException thrown = assertThrows(RuntimeException.class, () -> aspect.logServiceCall(joinPoint));

		assertEquals(boom, thrown);
		assertNull(MDC.get("layer"));
		assertNull(MDC.get("method"));
	}

	private static ProceedingJoinPoint joinPointFor(String signature) {
		ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
		Signature sig = mock(Signature.class);
		when(sig.toShortString()).thenReturn(signature);
		when(joinPoint.getSignature()).thenReturn(sig);
		return joinPoint;
	}

}
