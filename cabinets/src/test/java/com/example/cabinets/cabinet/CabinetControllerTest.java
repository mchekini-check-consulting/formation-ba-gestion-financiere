package com.example.cabinets.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

class CabinetControllerTest {

	private final CabinetService cabinets = mock(CabinetService.class);
	private final CabinetController controller = new CabinetController(cabinets);
	private final Cabinet cabinet = new Cabinet("Cabinet Martin", "812345678", "a@b.fr", 1);
	private final CabinetRequest requete = new CabinetRequest("Cabinet Martin", "812345678", "a@b.fr", 1);

	@Test
	void listerDelegueAuService() {
		when(cabinets.lister()).thenReturn(List.of(cabinet));
		assertEquals(List.of(cabinet), controller.lister());
	}

	@Test
	void lireDelegueAuService() {
		when(cabinets.lire(1L)).thenReturn(cabinet);
		assertEquals(cabinet, controller.lire(1L));
	}

	@Test
	void creerDelegueAuService() {
		when(cabinets.creer(requete)).thenReturn(cabinet);
		assertEquals(cabinet, controller.creer(requete));
	}

	@Test
	void modifierDelegueAuService() {
		when(cabinets.modifier(1L, requete)).thenReturn(cabinet);
		assertEquals(cabinet, controller.modifier(1L, requete));
	}

	@Test
	void supprimerDelegueAuService() {
		controller.supprimer(1L);
		verify(cabinets).supprimer(1L);
	}

}
