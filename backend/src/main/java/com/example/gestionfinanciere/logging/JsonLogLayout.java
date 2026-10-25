package com.example.gestionfinanciere.logging;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.LayoutBase;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class JsonLogLayout extends LayoutBase<ILoggingEvent> {

	private final ObjectMapper mapper = new ObjectMapper();

	@Override
	public String doLayout(ILoggingEvent event) {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("time", Instant.ofEpochMilli(event.getTimeStamp()).toString());
		fields.put("level", event.getLevel().toString().toLowerCase(Locale.ROOT));
		fields.put("msg", event.getFormattedMessage());
		fields.put("logger", event.getLoggerName());
		fields.putAll(event.getMDCPropertyMap());

		IThrowableProxy throwable = event.getThrowableProxy();
		if (throwable != null) {
			fields.put("err", throwable.getClassName() + ": " + throwable.getMessage());
		}

		try {
			return mapper.writeValueAsString(fields) + System.lineSeparator();
		} catch (JacksonException e) {
			return "{\"level\":\"error\",\"msg\":\"log serialization failed\"}" + System.lineSeparator();
		}
	}

}
