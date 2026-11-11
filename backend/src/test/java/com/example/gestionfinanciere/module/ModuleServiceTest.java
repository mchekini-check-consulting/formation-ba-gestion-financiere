package com.example.gestionfinanciere.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;

class ModuleServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-10T09:00:00Z");

	private final ModuleRepository modules = mock(ModuleRepository.class);
	private final ModuleService service = new ModuleService(modules);

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, MAINTENANT, MAINTENANT);
	private final Compte jean = Compte.administrateurDe(cabinet, "jean.dupont@cabinet-test.fr", "x", "Jean Dupont");

	// CA1 / RG1 : modules « Bientôt disponible », dans l'ordre réglé en base (RG3).
	@Test
	void ca1Modules() {
		ModuleDossier crm = new ModuleDossier("CRM", "CRM", "Relation client", ModuleDossier.Statut.BIENTOT_DISPONIBLE, 3);
		ModuleDossier temps = new ModuleDossier("SUIVI_TEMPS", "Suivi des temps", null,
				ModuleDossier.Statut.BIENTOT_DISPONIBLE, 4);
		when(modules.findAllByOrderByOrdreAscIdAsc()).thenReturn(List.of(crm, temps));

		assertEquals(List.of(
				new VueModule("CRM", "CRM", "Relation client", ModuleDossier.Statut.BIENTOT_DISPONIBLE),
				new VueModule("SUIVI_TEMPS", "Suivi des temps", null, ModuleDossier.Statut.BIENTOT_DISPONIBLE)),
				service.lister(jean));
	}

	@Test
	void entite() {
		ModuleDossier crm = new ModuleDossier("CRM", "CRM", "Relation client", ModuleDossier.Statut.BIENTOT_DISPONIBLE, 3);
		ReflectionTestUtils.setField(crm, "id", 1L);
		assertEquals(1L, crm.getId());
		assertEquals(3, crm.getOrdre());
	}

}
