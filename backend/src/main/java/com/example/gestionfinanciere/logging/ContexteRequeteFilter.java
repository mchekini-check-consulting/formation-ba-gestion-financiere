package com.example.gestionfinanciere.logging;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Identifiant de requête (champ {@code requestId}) écrit sur chaque ligne de log d'une même
 * requête HTTP, repris de l'en-tête {@value #EN_TETE} s'il est fourni, et renvoyé dans la
 * réponse. Vide aussi, en fin de requête, l'utilisateur et le cabinet posés par
 * {@link ContexteUtilisateur}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ContexteRequeteFilter extends OncePerRequestFilter {

	static final String EN_TETE = "X-Request-Id";
	static final String REQUEST_ID = "requestId";
	// Identifiant fourni par l'appelant accepté seulement s'il est court et sans caractère spécial.
	private static final Pattern ID_VALIDE = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chaine)
			throws ServletException, IOException {
		String fourni = request.getHeader(EN_TETE);
		String requestId = fourni != null && ID_VALIDE.matcher(fourni).matches() ? fourni
				: UUID.randomUUID().toString();
		MDC.put(REQUEST_ID, requestId);
		response.setHeader(EN_TETE, requestId);
		try {
			chaine.doFilter(request, response);
		} finally {
			MDC.remove(REQUEST_ID);
			ContexteUtilisateur.effacer();
		}
	}

}
