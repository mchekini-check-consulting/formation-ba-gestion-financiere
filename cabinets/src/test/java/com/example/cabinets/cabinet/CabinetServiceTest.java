package com.example.cabinets.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CabinetServiceTest {

	private final CabinetRepository cabinets = mock(CabinetRepository.class);
	private final CabinetService service = new CabinetService(cabinets);

	private static CabinetRequest valide() {
		return new CabinetRequest("Cabinet Martin", "812345678", "contact@martin.fr", 120);
	}

	private void sauvegardeRenvoieLArgument() {
		when(cabinets.save(any(Cabinet.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	private static HttpStatus statut(ResponseStatusException e) {
		return HttpStatus.valueOf(e.getStatusCode().value());
	}

	@Test
	void listerRenvoieLesCabinetsParId() {
		List<Cabinet> attendus = List.of(new Cabinet("a", "111111111", "a@b.fr", 0));
		when(cabinets.findAll(Sort.by("id"))).thenReturn(attendus);
		assertEquals(attendus, service.lister());
	}

	@Test
	void lireRenvoieLeCabinet() {
		Cabinet cabinet = new Cabinet("a", "111111111", "a@b.fr", 0);
		when(cabinets.findById(1L)).thenReturn(Optional.of(cabinet));
		assertSame(cabinet, service.lire(1L));
	}

	@Test
	void lireSignaleUnCabinetInconnu() {
		when(cabinets.findById(9L)).thenReturn(Optional.empty());
		ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.lire(9L));
		assertEquals(HttpStatus.NOT_FOUND, statut(e));
	}

	@Test
	void creerEnregistreUnCabinetNettoye() {
		sauvegardeRenvoieLArgument();
		Cabinet cree = service.creer(new CabinetRequest("  Cabinet Martin ", "812 345 678", " Contact@Martin.FR ", null));
		assertEquals("Cabinet Martin", cree.getNom());
		assertEquals("812345678", cree.getSiren());
		assertEquals("contact@martin.fr", cree.getEmail());
		assertEquals(0, cree.getNombreDossiers());
	}

	@Test
	void creerRefuseUnSirenDejaUtilise() {
		when(cabinets.existsBySiren("812345678")).thenReturn(true);
		ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.creer(valide()));
		assertEquals(HttpStatus.CONFLICT, statut(e));
		verify(cabinets, never()).save(any(Cabinet.class));
	}

	@ParameterizedTest
	@CsvSource(nullValues = "NULL", value = {
			"NULL, 812345678, a@b.fr, 1",
			"'  ', 812345678, a@b.fr, 1",
			"Nom, NULL, a@b.fr, 1",
			"Nom, 12345, a@b.fr, 1",
			"Nom, 81234567A, a@b.fr, 1",
			"Nom, 812345678, NULL, 1",
			"Nom, 812345678, pas-un-email, 1",
			"Nom, 812345678, a@b.fr, -1",
	})
	void creerRefuseUneSaisieInvalide(String nom, String siren, String email, Integer dossiers) {
		CabinetRequest requete = new CabinetRequest(nom, siren, email, dossiers);
		ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.creer(requete));
		assertEquals(HttpStatus.BAD_REQUEST, statut(e));
		verify(cabinets, never()).save(any(Cabinet.class));
	}

	@Test
	void creerRefuseDesTextesTropLongs() {
		CabinetRequest nomLong = new CabinetRequest("x".repeat(201), "812345678", "a@b.fr", 1);
		assertThrows(ResponseStatusException.class, () -> service.creer(nomLong));
		CabinetRequest emailLong = new CabinetRequest("Nom", "812345678", "a".repeat(250) + "@b.fr", 1);
		assertThrows(ResponseStatusException.class, () -> service.creer(emailLong));
	}

	@Test
	void modifierMetAJourLeCabinet() {
		Cabinet existant = new Cabinet("Ancien", "111111111", "old@b.fr", 1);
		when(cabinets.findById(1L)).thenReturn(Optional.of(existant));
		sauvegardeRenvoieLArgument();
		Cabinet modifie = service.modifier(1L, valide());
		assertSame(existant, modifie);
		assertEquals("Cabinet Martin", modifie.getNom());
		assertEquals("812345678", modifie.getSiren());
		assertEquals(120, modifie.getNombreDossiers());
	}

	@Test
	void modifierRefuseLeSirenDUnAutreCabinet() {
		when(cabinets.findById(1L)).thenReturn(Optional.of(new Cabinet("a", "111111111", "a@b.fr", 0)));
		when(cabinets.existsBySirenAndIdNot("812345678", 1L)).thenReturn(true);
		ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.modifier(1L, valide()));
		assertEquals(HttpStatus.CONFLICT, statut(e));
	}

	@Test
	void supprimerEfface() {
		Cabinet cabinet = new Cabinet("a", "111111111", "a@b.fr", 0);
		when(cabinets.findById(1L)).thenReturn(Optional.of(cabinet));
		service.supprimer(1L);
		verify(cabinets).delete(cabinet);
	}

}
