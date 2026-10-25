package com.example.gestionfinanciere.logging;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(RequestLoggingInterceptor.class);
	private static final String START_ATTRIBUTE = RequestLoggingInterceptor.class.getName() + ".start";

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		request.setAttribute(START_ATTRIBUTE, System.nanoTime());
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) {
		Object start = request.getAttribute(START_ATTRIBUTE);
		long durationMs = start instanceof Long startNanos
				? Duration.ofNanos(System.nanoTime() - startNanos).toMillis()
				: 0;
		Object routePattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		String route = routePattern != null ? routePattern.toString() : "unmatched";

		MdcContext ctx = MdcContext.open()
				.with("layer", "controller")
				.with("method", request.getMethod())
				.with("route", route)
				.with("status", String.valueOf(response.getStatus()))
				.with("durationMs", String.valueOf(durationMs));
		try {
			log.info("http request handled");
		} finally {
			ctx.close();
		}
	}

}
