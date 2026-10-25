package com.example.gestionfinanciere.logging;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.MDC;

/**
 * Pose des valeurs MDC et restaure les valeurs précédentes à la fermeture,
 * pour ne pas écraser le contexte d'un appel englobant (ex. "service" dans "db").
 */
final class MdcContext implements AutoCloseable {

	private final Map<String, String> previousValues = new LinkedHashMap<>();

	private MdcContext() {
	}

	static MdcContext open() {
		return new MdcContext();
	}

	MdcContext with(String key, String value) {
		previousValues.put(key, MDC.get(key));
		MDC.put(key, value);
		return this;
	}

	@Override
	public void close() {
		previousValues.forEach((key, previous) -> {
			if (previous == null) {
				MDC.remove(key);
			} else {
				MDC.put(key, previous);
			}
		});
	}

}
