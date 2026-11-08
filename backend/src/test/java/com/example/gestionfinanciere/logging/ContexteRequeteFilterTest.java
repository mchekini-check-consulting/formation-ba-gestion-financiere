package com.example.gestionfinanciere.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ContexteRequeteFilterTest {

	private final ContexteRequeteFilter filtre = new ContexteRequeteFilter();
	private final Map<String, String> pendantLaRequete = new HashMap<>();

	@AfterEach
	void vider() {
		MDC.clear();
	}

	private MockHttpServletResponse traiter(MockHttpServletRequest requete) throws Exception {
		MockHttpServletResponse reponse = new MockHttpServletResponse();
		filtre.doFilter(requete, reponse, (req, rep) -> {
			// La session reconnue pose l'utilisateur et le cabinet (SessionService).
			ContexteUtilisateur.definir(42L, 7L);
			pendantLaRequete.putAll(MDC.getCopyOfContextMap());
		});
		return reponse;
	}

	@Test
	void chaqueRequeteRecoitUnIdentifiantRenvoyeDansLaReponse() throws Exception {
		MockHttpServletResponse reponse = traiter(new MockHttpServletRequest("GET", "/dossiers"));

		String requestId = pendantLaRequete.get("requestId");
		assertNotNull(requestId);
		assertEquals(requestId, reponse.getHeader("X-Request-Id"));
		assertEquals("42", pendantLaRequete.get("utilisateur"));
		assertEquals("7", pendantLaRequete.get("cabinet"));
	}

	@Test
	void identifiantFourniParLAppelantRepris() throws Exception {
		MockHttpServletRequest requete = new MockHttpServletRequest("GET", "/dossiers");
		requete.addHeader("X-Request-Id", "abc-12345678");

		traiter(requete);

		assertEquals("abc-12345678", pendantLaRequete.get("requestId"));
	}

	@Test
	void identifiantFourniInvalideRemplace() throws Exception {
		MockHttpServletRequest requete = new MockHttpServletRequest("GET", "/dossiers");
		requete.addHeader("X-Request-Id", "<script>\"injection\"");

		traiter(requete);

		assertNotEquals("<script>\"injection\"", pendantLaRequete.get("requestId"));
	}

	@Test
	void contexteEffaceEnFinDeRequete() throws Exception {
		traiter(new MockHttpServletRequest("GET", "/dossiers"));

		assertNull(MDC.get("requestId"));
		assertNull(MDC.get("utilisateur"));
		assertNull(MDC.get("cabinet"));
	}

	@Test
	void compteSansCabinetSansChampCabinet() {
		ContexteUtilisateur.definir(42L, 7L);
		ContexteUtilisateur.definir(43L, null);

		assertEquals("43", MDC.get("utilisateur"));
		assertNull(MDC.get("cabinet"));
	}

}
