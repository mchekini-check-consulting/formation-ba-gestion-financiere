package com.example.gestionfinanciere.logging;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** Capture, dans un test, les lignes de log écrites par une classe. À fermer en fin de test. */
public final class JournalCapture implements AutoCloseable {

	private final Logger logger;
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	private JournalCapture(Class<?> classe) {
		logger = (Logger) LoggerFactory.getLogger(classe);
		appender.start();
		logger.addAppender(appender);
	}

	public static JournalCapture de(Class<?> classe) {
		return new JournalCapture(classe);
	}

	public List<ILoggingEvent> lignes() {
		return List.copyOf(appender.list);
	}

	/** Seule ligne écrite au niveau demandé (échoue s'il y en a zéro ou plusieurs). */
	public ILoggingEvent seule(Level niveau) {
		List<ILoggingEvent> lignes = appender.list.stream().filter(e -> e.getLevel() == niveau).toList();
		if (lignes.size() != 1) {
			throw new AssertionError("une ligne " + niveau + " attendue, trouvées : " + lignes.size());
		}
		return lignes.get(0);
	}

	/** Champs métier d'une ligne (addKeyValue), en texte. */
	public static Map<String, String> champs(ILoggingEvent ligne) {
		if (ligne.getKeyValuePairs() == null) {
			return Map.of();
		}
		return ligne.getKeyValuePairs().stream()
				.collect(Collectors.toMap(p -> p.key, p -> String.valueOf(p.value)));
	}

	@Override
	public void close() {
		logger.detachAppender(appender);
	}

}
