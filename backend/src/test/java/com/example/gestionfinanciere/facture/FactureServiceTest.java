package com.example.gestionfinanciere.facture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
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
		assertEquals(List.of("facture-electricite-octobre.pdf", "ticket-fournitures.png", "facture-client-0042.pdf"),
				liste.stream().map(Facture::getNomFichier).toList());
		assertEquals(List.of(Facture.Sens.ACHAT, Facture.Sens.ACHAT, Facture.Sens.VENTE),
				liste.stream().map(Facture::getSens).toList());
		assertEquals(List.of(false, true, true), liste.stream().map(f -> f.getOuverteLe() == null).toList());
		assertTrue(liste.stream().allMatch(f -> f.getDossier() == fleurs && f.getDeposeePar() == jean));
		// US-13 : chaque exemple a un vrai fichier (PDF ou PNG).
		assertEquals(List.of("application/pdf", "image/png", "application/pdf"),
				liste.stream().map(Facture::getTypeFichier).toList());
		assertEquals("%PDF", new String(liste.get(0).getContenu(), 0, 4, StandardCharsets.US_ASCII));
		assertEquals("PNG", new String(liste.get(1).getContenu(), 1, 3, StandardCharsets.US_ASCII));
	}

	@Test
	void fichierDExempleAbsent() {
		assertThrows(IllegalStateException.class, () -> FactureService.exemple("inconnu.pdf"));
	}

	private Facture avecFichier(Instant ouverteLe) {
		Facture facture = new Facture(fleurs, Facture.Sens.ACHAT, "edf.pdf", "application/pdf", MAINTENANT, jean,
				new byte[] { 1, 2, 3 });
		ReflectionTestUtils.setField(facture, "id", 5L);
		ReflectionTestUtils.setField(fleurs, "id", 42L);
		if (ouverteLe != null) {
			facture.marquerOuverte(ouverteLe);
		}
		return facture;
	}

	// US-13 RG1 et RG2 : fichier d'origine ; l'aperçu fait disparaître le badge « Nouvelle ».
	@Test
	void us13ApercuEtTelechargement() {
		Facture facture = avecFichier(null);
		when(factures.findByIdAndDossierId(5L, 42L)).thenReturn(Optional.of(facture));

		assertEquals(Optional.of(new VueFacture(5L, "edf.pdf", "application/pdf", Facture.Sens.ACHAT)),
				service(false).consulter(jean, 42L, 5L));
		assertTrue(LigneFacture.de(facture).nouvelle());

		FichierFacture fichier = service(false).ouvrir(jean, 42L, 5L, false).orElseThrow();
		assertEquals(new FichierFacture("edf.pdf", "application/pdf", new byte[] { 1, 2, 3 }), fichier);
		assertEquals(MAINTENANT, facture.getOuverteLe());
		assertFalse(LigneFacture.de(facture).nouvelle());

		assertTrue(service(false).ouvrir(jean, 42L, 5L, true).isPresent());
	}

	// US-13 RG3 : facture d'un autre dossier, dossier d'un autre cabinet, facture sans fichier.
	@Test
	void us13SansDroitsOuSansFichier() {
		Facture sansFichier = facture(Facture.Sens.ACHAT, "vide.pdf", null);
		ReflectionTestUtils.setField(fleurs, "id", 42L);
		when(factures.findByIdAndDossierId(6L, 42L)).thenReturn(Optional.of(sansFichier));

		assertTrue(service(false).ouvrir(jean, 42L, 6L, false).isEmpty());
		assertTrue(service(false).ouvrir(jean, 42L, 7L, false).isEmpty());
		assertTrue(service(false).ouvrir(jean, 99L, 5L, true).isEmpty());
		assertTrue(service(false).consulter(jean, 99L, 5L).isEmpty());
		assertNull(sansFichier.getOuverteLe());
	}

	// Logs : aperçu et téléchargement (info), refus (warn) ; jamais le contenu ni le nom du fichier.
	@Test
	void us13Logs() {
		when(factures.findByIdAndDossierId(5L, 42L)).thenReturn(Optional.of(avecFichier(null)));
		try (JournalCapture journal = JournalCapture.de(EvenementMetier.class)) {
			service(false).ouvrir(jean, 42L, 5L, false);
			service(false).ouvrir(jean, 42L, 5L, true);
			service(false).ouvrir(jean, 99L, 5L, true);

			List<String> evenements = journal.lignes().stream().map(e -> e.getLevel() + " "
					+ e.getMDCPropertyMap().get("event") + "=" + e.getMDCPropertyMap().get("outcome")).toList();
			assertEquals(List.of("INFO facture.apercu=ouverte", "INFO facture.telechargement=ouverte",
					"WARN facture.telechargement=introuvable"), evenements);
			assertEquals("5", journal.lignes().get(0).getMDCPropertyMap().get("factureId"));
			assertTrue(journal.lignes().stream().noneMatch(e -> e.getMDCPropertyMap().toString().contains("edf.pdf")));
		}
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
