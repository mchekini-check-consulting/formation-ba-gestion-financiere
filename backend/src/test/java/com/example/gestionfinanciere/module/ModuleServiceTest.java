package com.example.gestionfinanciere.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.dossier.Dossier;
import com.example.gestionfinanciere.dossier.DossierRepository;

class ModuleServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-10T09:00:00Z");

	private final ModuleRepository modules = mock(ModuleRepository.class);
	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final ModuleService service = new ModuleService(modules, dossiers);

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, MAINTENANT, MAINTENANT);
	private final Compte jean = Compte.administrateurDe(cabinet, "jean.dupont@cabinet-test.fr", "x", "Jean Dupont");
	private final Dossier leroy = new Dossier(cabinet, "Boulangerie Leroy SARL", "812345679", jean, MAINTENANT);

	@BeforeEach
	void base() {
		ReflectionTestUtils.setField(cabinet, "id", 7L);
		ReflectionTestUtils.setField(jean, "id", 3L);
		when(dossiers.findByIdAndCabinetId(42L, 7L)).thenReturn(Optional.of(leroy));
	}

	// CA1 / RG1 : CRM et Suivi des temps, « Bientôt disponible », dans l'ordre réglé en base (RG3).
	@Test
	void ca1ModulesDuDossier() {
		ModuleDossier crm = new ModuleDossier("CRM", "CRM", "Relation client", ModuleDossier.Statut.BIENTOT_DISPONIBLE, 1);
		ModuleDossier temps = new ModuleDossier("SUIVI_TEMPS", "Suivi des temps", null,
				ModuleDossier.Statut.BIENTOT_DISPONIBLE, 2);
		when(modules.findAllByOrderByOrdreAscIdAsc()).thenReturn(List.of(crm, temps));

		assertEquals(Optional.of(List.of(
				new VueModule("CRM", "CRM", "Relation client", ModuleDossier.Statut.BIENTOT_DISPONIBLE),
				new VueModule("SUIVI_TEMPS", "Suivi des temps", null, ModuleDossier.Statut.BIENTOT_DISPONIBLE))),
				service.lister(jean, 42L));
	}

	// US-11 RG3 : dossier inexistant ou d'un autre cabinet -> aucun module.
	@Test
	void dossierIntrouvable() {
		when(dossiers.findByIdAndCabinetId(43L, 7L)).thenReturn(Optional.empty());

		assertTrue(service.lister(jean, 43L).isEmpty());
		verify(modules, never()).findAllByOrderByOrdreAscIdAsc();
	}

	@Test
	void entite() {
		ModuleDossier crm = new ModuleDossier("CRM", "CRM", "Relation client", ModuleDossier.Statut.BIENTOT_DISPONIBLE, 1);
		ReflectionTestUtils.setField(crm, "id", 1L);
		assertEquals(1L, crm.getId());
		assertEquals(1, crm.getOrdre());
	}

}
