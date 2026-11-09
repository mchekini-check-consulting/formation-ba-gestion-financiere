package com.example.gestionfinanciere.facture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.HorlogeReglable;
import com.example.gestionfinanciere.dossier.Dossier;
import com.example.gestionfinanciere.dossier.DossierRepository;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.JournalCapture;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

class FactureServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-08T09:00:00Z");

	private final FactureRepository factures = mock(FactureRepository.class);
	private final DossierRepository dossiers = mock(DossierRepository.class);

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, MAINTENANT, MAINTENANT);
	private final Compte jean = Compte.administrateurDe(cabinet, "jean.dupont@cabinet-test.fr", "x", "Jean Dupont");
	private final Dossier fleurs = new Dossier(cabinet, "Fleurs Martin EURL", "812345679", jean, MAINTENANT);

	private FactureService service(boolean modeTest) {
		return new FactureService(factures, dossiers, new HorlogeReglable(MAINTENANT), modeTest);
	}

	@BeforeEach
	void base() {
		ReflectionTestUtils.setField(cabinet, "id", 7L);
		ReflectionTestUtils.setField(jean, "id", 3L);
		when(dossiers.findByIdAndCabinetId(42L, 7L)).thenReturn(Optional.of(fleurs));
	}

	private Facture facture(Facture.Sens sens, String nom, Instant ouverteLe) {
		Facture facture = new Facture(fleurs, sens, nom, "application/pdf", MAINTENANT, jean);
		ReflectionTestUtils.setField(facture, "id", 5L);
		if (ouverteLe != null) {
			facture.marquerOuverte(ouverteLe);
		}
		return facture;
	}

	// RG1 et RG2 : factures de l'onglet (fichier, date, qui l'a déposée, « Reçue ») ; RG3 : nouvelle ou non.
	@Test
	void rg1Rg2Rg3FacturesDeLOnglet() {
		when(factures.findByDossierIdAndSensOrderByDeposeeLeDescIdDesc(42L, Facture.Sens.ACHAT)).thenReturn(List.of(
				facture(Facture.Sens.ACHAT, "edf.pdf", null), facture(Facture.Sens.ACHAT, "loyer.pdf", MAINTENANT)));

		ListeFactures liste = service(false).lister(jean, 42L, Facture.Sens.ACHAT).orElseThrow();

		assertEquals(List.of(new LigneFacture(5L, "edf.pdf", MAINTENANT, "Jean Dupont", Facture.Statut.RECUE, true),
				new LigneFacture(5L, "loyer.pdf", MAINTENANT, "Jean Dupont", Facture.Statut.RECUE, false)),
				liste.factures());
		assertFalse(liste.exemplesPossibles());
		assertTrue(service(true).lister(jean, 42L, Facture.Sens.VENTE).orElseThrow().exemplesPossibles());
	}

	// Droits du dossier (US-11 RG3) : dossier d'un autre cabinet -> rien.
	@Test
	void dossierDUnAutreCabinet() {
		assertTrue(service(true).lister(jean, 99L, Facture.Sens.ACHAT).isEmpty());
		assertFalse(service(true).ajouterExemples(jean, 99L));
		verify(factures, never()).findByDossierIdAndSensOrderByDeposeeLeDescIdDesc(any(), any());
		verify(factures, never()).saveAll(any());
	}

	// Mode test : deux factures fournisseurs (une déjà ouverte) et une facture client.
	@Test
	@SuppressWarnings("unchecked")
	void exemplesEnModeTestSeulement() {
		assertFalse(service(false).ajouterExemples(jean, 42L));
		verify(factures, never()).saveAll(any());

		assertTrue(service(true).ajouterExemples(jean, 42L));
		ArgumentCaptor<Iterable<Facture>> enregistrees = ArgumentCaptor.forClass(Iterable.class);
		verify(factures).saveAll(enregistrees.capture());
		List<Facture> liste = new ArrayList<>();
		enregistrees.getValue().forEach(liste::add);
		assertEquals(List.of("facture-electricite-octobre.pdf", "ticket-fournitures.jpg", "facture-client-0042.pdf"),
				liste.stream().map(Facture::getNomFichier).toList());
		assertEquals(List.of(Facture.Sens.ACHAT, Facture.Sens.ACHAT, Facture.Sens.VENTE),
				liste.stream().map(Facture::getSens).toList());
		assertEquals(List.of(false, true, true), liste.stream().map(f -> f.getOuverteLe() == null).toList());
		assertTrue(liste.stream().allMatch(f -> f.getDossier() == fleurs && f.getDeposeePar() == jean));
	}

	// Logs : consultation (info) avec dossier, onglet et nombre ; refus d'accès (warn) ; jamais de nom de fichier.
	@Test
	void logs() {
		when(factures.findByDossierIdAndSensOrderByDeposeeLeDescIdDesc(42L, Facture.Sens.VENTE))
				.thenReturn(List.of(facture(Facture.Sens.VENTE, "client.pdf", null)));
		try (JournalCapture journal = JournalCapture.de(EvenementMetier.class)) {
			service(false).lister(jean, 42L, Facture.Sens.VENTE);
			service(false).lister(jean, 99L, Facture.Sens.VENTE);

			ILoggingEvent consultation = journal.seule(Level.INFO);
			Map<String, String> champs = consultation.getMDCPropertyMap();
			assertEquals("facture.liste", champs.get("event"));
			assertEquals("consultee", champs.get("outcome"));
			assertEquals("42", champs.get("dossierId"));
			assertEquals("VENTE", champs.get("sens"));
			assertEquals("1", champs.get("nombre"));
			assertEquals("3", champs.get("compteId"));
			assertEquals("7", champs.get("cabinetId"));
			assertFalse(champs.toString().contains("client.pdf"));

			ILoggingEvent refus = journal.seule(Level.WARN);
			assertEquals("introuvable", refus.getMDCPropertyMap().get("outcome"));
			assertEquals("99", refus.getMDCPropertyMap().get("dossierId"));
		}
	}

}
