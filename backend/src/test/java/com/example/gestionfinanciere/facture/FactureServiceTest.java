package com.example.gestionfinanciere.facture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.HorlogeReglable;
import com.example.gestionfinanciere.compte.RoleCabinet;
import com.example.gestionfinanciere.dossier.Dossier;
import com.example.gestionfinanciere.dossier.DossierRepository;
import com.example.gestionfinanciere.ecriture.EcritureRepository;
import com.example.gestionfinanciere.ecriture.EcritureService;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.JournalCapture;
import com.example.gestionfinanciere.facture.lecture.DemandeLecture;
import com.example.gestionfinanciere.plancomptable.CompteComptableRepository;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

class FactureServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-08T09:00:00Z");

	private final FactureRepository factures = mock(FactureRepository.class);
	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final ChangementStatutFactureRepository historique = mock(ChangementStatutFactureRepository.class);
	private final ChampLuRepository champsLus = mock(ChampLuRepository.class);
	private final CompteComptableRepository comptes = mock(CompteComptableRepository.class);
	private final CorrectionFactureRepository corrections = mock(CorrectionFactureRepository.class);
	private final ApplicationEventPublisher evenements = mock(ApplicationEventPublisher.class);

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, MAINTENANT, MAINTENANT);
	private final Compte jean = Compte.administrateurDe(cabinet, "jean.dupont@cabinet-test.fr", "x", "Jean Dupont");
	private final Dossier fleurs = new Dossier(cabinet, "Fleurs Martin EURL", "812345679", jean, MAINTENANT);

	private FactureService service() {
		return new FactureService(factures, historique, new VuesFacture(champsLus, comptes, corrections,
				new EcrituresFacture(new EcritureService(mock(EcritureRepository.class), comptes),
						mock(CorrectionFactureRepository.class))), dossiers,
				evenements, new HorlogeReglable(MAINTENANT));
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

		ListeFactures liste = service().lister(jean, 42L, Facture.Sens.ACHAT).orElseThrow();

		List<Facture.Statut> possibles = List.of(Facture.Statut.EN_COURS, Facture.Statut.A_COMPLETER);
		assertEquals(List.of(
				new LigneFacture(5L, "edf.pdf", MAINTENANT, "Jean Dupont", Facture.Statut.RECUE, true, null, possibles,
						Facture.Source.DEPOT_CLIENT, null, false, null, null, null),
				new LigneFacture(5L, "loyer.pdf", MAINTENANT, "Jean Dupont", Facture.Statut.RECUE, false, null,
						possibles, Facture.Source.DEPOT_CLIENT, null, false, null, null, null)),
				liste.factures());
	}

	// Droits du dossier (US-11 RG3) : dossier d'un autre cabinet -> rien.
	@Test
	void dossierDUnAutreCabinet() {
		assertTrue(service().lister(jean, 99L, Facture.Sens.ACHAT).isEmpty());
		verify(factures, never()).findByDossierIdAndSensOrderByDeposeeLeDescIdDesc(any(), any());
		verify(factures, never()).saveAll(any());
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

		assertEquals(Optional.of(new VueFacture(5L, "edf.pdf", "application/pdf", Facture.Sens.ACHAT, Facture.Statut.RECUE, null, null, null)),
				service().consulter(jean, 42L, 5L));
		assertTrue(LigneFacture.de(facture).nouvelle());

		FichierFacture fichier = service().ouvrir(jean, 42L, 5L, false).orElseThrow();
		assertEquals(new FichierFacture("edf.pdf", "application/pdf", new byte[] { 1, 2, 3 }), fichier);
		assertEquals(MAINTENANT, facture.getOuverteLe());
		assertFalse(LigneFacture.de(facture).nouvelle());

		assertTrue(service().ouvrir(jean, 42L, 5L, true).isPresent());
	}

	// US-13 RG3 : facture d'un autre dossier, dossier d'un autre cabinet, facture sans fichier.
	@Test
	void us13SansDroitsOuSansFichier() {
		Facture sansFichier = facture(Facture.Sens.ACHAT, "vide.pdf", null);
		ReflectionTestUtils.setField(fleurs, "id", 42L);
		when(factures.findByIdAndDossierId(6L, 42L)).thenReturn(Optional.of(sansFichier));

		assertTrue(service().ouvrir(jean, 42L, 6L, false).isEmpty());
		assertTrue(service().ouvrir(jean, 42L, 7L, false).isEmpty());
		assertTrue(service().ouvrir(jean, 99L, 5L, true).isEmpty());
		assertTrue(service().consulter(jean, 99L, 5L).isEmpty());
		assertNull(sansFichier.getOuverteLe());
	}

	// Logs : aperçu et téléchargement (info), refus (warn) ; jamais le contenu ni le nom du fichier.
	@Test
	void us13Logs() {
		when(factures.findByIdAndDossierId(5L, 42L)).thenReturn(Optional.of(avecFichier(null)));
		try (JournalCapture journal = JournalCapture.de(EvenementMetier.class)) {
			service().ouvrir(jean, 42L, 5L, false);
			service().ouvrir(jean, 42L, 5L, true);
			service().ouvrir(jean, 99L, 5L, true);

			List<String> evenements = journal.lignes().stream().map(e -> e.getLevel() + " "
					+ e.getMDCPropertyMap().get("event") + "=" + e.getMDCPropertyMap().get("outcome")).toList();
			assertEquals(List.of("INFO facture.apercu=ouverte", "INFO facture.telechargement=ouverte",
					"WARN facture.telechargement=introuvable"), evenements);
			assertEquals("5", journal.lignes().get(0).getMDCPropertyMap().get("factureId"));
			assertTrue(journal.lignes().stream().noneMatch(e -> e.getMDCPropertyMap().toString().contains("edf.pdf")));
		}
	}

	private Facture edf() {
		Facture facture = facture(Facture.Sens.ACHAT, "facture-edf.pdf", null);
		ReflectionTestUtils.setField(fleurs, "id", 42L);
		when(factures.findByIdAndDossierId(5L, 42L)).thenReturn(Optional.of(facture));
		return facture;
	}

	// US-51 CA1 : Reçue -> En cours de traitement -> Traitée ; RG4 : chaque changement garde auteur et date.
	@Test
	void us51Ca1EnCoursPuisTraitee() {
		Facture facture = edf();

		ResultatStatut enCours = service().changerStatut(jean, 42L, 5L, Facture.Statut.EN_COURS, null);
		assertEquals(ResultatStatut.Issue.CHANGE, enCours.issue());
		assertEquals(Facture.Statut.EN_COURS, enCours.facture().statut());
		ResultatStatut traitee = service().changerStatut(jean, 42L, 5L, Facture.Statut.TRAITEE, " ");
		assertEquals(Facture.Statut.TRAITEE, traitee.facture().statut());
		assertEquals(Facture.Statut.TRAITEE, facture.getStatut());

		ArgumentCaptor<ChangementStatutFacture> changements = ArgumentCaptor.forClass(ChangementStatutFacture.class);
		verify(historique, times(2)).save(changements.capture());
		ChangementStatutFacture dernier = changements.getAllValues().get(1);
		assertEquals(Facture.Statut.EN_COURS, dernier.getAncienStatut());
		assertEquals(Facture.Statut.TRAITEE, dernier.getNouveauStatut());
		assertSame(jean, dernier.getModifiePar());
		assertEquals(MAINTENANT, dernier.getModifieLe());
		assertNull(dernier.getMotif());
	}

	// US-51 CA2 : « À compléter » avec motif (espaces retirés), gardé dans l'historique.
	@Test
	void us51Ca2ACompleterAvecMotif() {
		edf();
		ResultatStatut resultat = service().changerStatut(jean, 42L, 5L, Facture.Statut.A_COMPLETER,
				"  Facture illisible ");
		assertEquals(ResultatStatut.Issue.CHANGE, resultat.issue());
		assertEquals("Facture illisible", resultat.facture().motif());
		ArgumentCaptor<ChangementStatutFacture> changement = ArgumentCaptor.forClass(ChangementStatutFacture.class);
		verify(historique).save(changement.capture());
		assertEquals("Facture illisible", changement.getValue().getMotif());
	}

	// US-51 CA3 (motif manquant ou trop long), CA4 (passage interdit), autre cabinet : rien ne change.
	@Test
	void us51Refus() {
		Facture facture = edf();
		FactureService service = service();

		assertEquals(ResultatStatut.Issue.MOTIF_MANQUANT,
				service.changerStatut(jean, 42L, 5L, Facture.Statut.A_COMPLETER, null).issue());
		assertEquals(ResultatStatut.Issue.MOTIF_MANQUANT,
				service.changerStatut(jean, 42L, 5L, Facture.Statut.A_COMPLETER, "   ").issue());
		assertEquals(ResultatStatut.Issue.MOTIF_TROP_LONG,
				service.changerStatut(jean, 42L, 5L, Facture.Statut.A_COMPLETER, "x".repeat(256)).issue());
		assertEquals(ResultatStatut.Issue.PASSAGE_INTERDIT,
				service.changerStatut(jean, 42L, 5L, Facture.Statut.TRAITEE, null).issue());
		assertEquals(ResultatStatut.Issue.INTROUVABLE,
				service.changerStatut(jean, 99L, 5L, Facture.Statut.EN_COURS, null).issue());
		assertNull(service.changerStatut(jean, 42L, 6L, Facture.Statut.EN_COURS, null).facture());

		assertEquals(Facture.Statut.RECUE, facture.getStatut());
		verify(historique, never()).save(any());
	}

	// Logs : changement (info) avec ancien et nouveau statut, refus (warn) ; jamais le motif ni le nom du fichier.
	@Test
	void us51Logs() {
		edf();
		try (JournalCapture journal = JournalCapture.de(EvenementMetier.class)) {
			service().changerStatut(jean, 42L, 5L, Facture.Statut.A_COMPLETER, "Facture illisible");
			service().changerStatut(jean, 42L, 5L, Facture.Statut.TRAITEE, null);

			Map<String, String> change = journal.seule(Level.INFO).getMDCPropertyMap();
			assertEquals("facture.statut", change.get("event"));
			assertEquals("change", change.get("outcome"));
			assertEquals("RECUE", change.get("ancienStatut"));
			assertEquals("A_COMPLETER", change.get("nouveauStatut"));
			assertEquals("passage_interdit", journal.seule(Level.WARN).getMDCPropertyMap().get("outcome"));
			assertTrue(journal.lignes().stream()
					.noneMatch(e -> e.getMDCPropertyMap().toString().contains("illisible")
							|| e.getFormattedMessage().contains("illisible")
							|| e.getMDCPropertyMap().toString().contains("edf")));
		}
	}

	// Logs : consultation (info) avec dossier, onglet et nombre ; refus d'accès (warn) ; jamais de nom de fichier.
	@Test
	void logs() {
		when(factures.findByDossierIdAndSensOrderByDeposeeLeDescIdDesc(42L, Facture.Sens.VENTE))
				.thenReturn(List.of(facture(Facture.Sens.VENTE, "client.pdf", null)));
		try (JournalCapture journal = JournalCapture.de(EvenementMetier.class)) {
			service().lister(jean, 42L, Facture.Sens.VENTE);
			service().lister(jean, 99L, Facture.Sens.VENTE);

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

	// ---------- US-52 Importer des factures depuis le dossier ----------

	private static final byte[] PDF = "%PDF-1.7 facture".getBytes(StandardCharsets.US_ASCII);

	private Facture enregistree() {
		ArgumentCaptor<Facture> capture = ArgumentCaptor.forClass(Facture.class);
		verify(factures).save(capture.capture());
		return capture.getValue();
	}

	// CA1, RG1 et RG4 : facture de l'onglet, « Reçue », « Import cabinet », visible tout de suite (« Nouvelle »).
	@Test
	void us52Ca1Import() {
		when(factures.save(any(Facture.class))).thenAnswer(appel -> {
			Facture facture = appel.getArgument(0);
			ReflectionTestUtils.setField(facture, "id", 9L);
			return facture;
		});

		ResultatImport resultat = service().importer(jean, 42L, Facture.Sens.ACHAT,
				"C:\\docs\\facture-edf-juin.pdf", PDF);

		assertEquals(ResultatImport.Statut.IMPORTEE, resultat.statut());
		assertEquals(new LigneFacture(9L, "facture-edf-juin.pdf", MAINTENANT, "Jean Dupont", Facture.Statut.RECUE, true,
				null, Facture.Statut.RECUE.suivants(), Facture.Source.IMPORT_CABINET, Facture.StatutLecture.EN_COURS,
				false, null, null, null), resultat.facture());
		Facture facture = enregistree();
		assertEquals("application/pdf", facture.getTypeFichier());
		assertEquals(Facture.Sens.ACHAT, facture.getSens());
		assertEquals(fleurs, facture.getDossier());
		// US-53 RG1 : facture fournisseur importée par le cabinet -> lue en arrière-plan, comme un dépôt.
		verify(evenements).publishEvent(new DemandeLecture(9L));
	}

	// US-54 RG1 : une facture client importée est lue elle aussi.
	@Test
	void us54ImportFactureClientLue() {
		when(factures.save(any(Facture.class))).thenAnswer(appel -> {
			Facture facture = appel.getArgument(0);
			ReflectionTestUtils.setField(facture, "id", 10L);
			return facture;
		});

		ResultatImport resultat = service().importer(jean, 42L, Facture.Sens.VENTE, "vente.pdf", PDF);

		assertEquals(ResultatImport.Statut.IMPORTEE, resultat.statut());
		assertEquals(Facture.StatutLecture.EN_COURS, resultat.facture().lecture());
		verify(evenements).publishEvent(new DemandeLecture(10L));
	}

	// CA2 et RG2 : ni PDF, ni JPG, ni PNG ; ou plus de 10 Mo.
	@Test
	void us52Ca2FichierRefuse() {
		byte[] xlsx = { 'P', 'K', 3, 4, 0, 0 };
		assertEquals(ResultatImport.Statut.FORMAT_REFUSE,
				service().importer(jean, 42L, Facture.Sens.VENTE, "releve.xlsx", xlsx).statut());
		assertEquals(ResultatImport.Statut.FORMAT_REFUSE,
				service().importer(jean, 42L, Facture.Sens.VENTE, "vide.pdf", null).statut());
		byte[] gros = new byte[(int) DepotFactureService.TAILLE_MAX + 1];
		assertEquals(ResultatImport.Statut.TROP_VOLUMINEUX,
				service().importer(jean, 42L, Facture.Sens.ACHAT, "gros.pdf", gros).statut());
		verify(factures, never()).save(any());
	}

	// CA3 et RG3 : dossier d'un autre cabinet, ou non attribué au comptable -> refus, rien d'enregistré.
	@Test
	void us52Ca3DossierNonAttribue() {
		Compte lucie = Compte.invite(cabinet, "lucie@cabinet-test.fr", "x", "Lucie Moreau", RoleCabinet.COMPTABLE);
		ReflectionTestUtils.setField(lucie, "id", 5L);
		ReflectionTestUtils.setField(fleurs, "id", 42L);

		assertEquals(ResultatImport.Statut.DOSSIER_INTROUVABLE,
				service().importer(jean, 99L, Facture.Sens.ACHAT, "edf.pdf", PDF).statut());
		assertEquals(ResultatImport.Statut.DOSSIER_INTROUVABLE,
				service().importer(lucie, 42L, Facture.Sens.ACHAT, "edf.pdf", PDF).statut());
		verify(factures, never()).save(any());

		// Une fois le dossier attribué à Lucie (US-20), l'import est possible.
		when(dossiers.estAttribue(42L, 5L)).thenReturn(true);
		when(factures.save(any(Facture.class))).thenAnswer(appel -> appel.getArgument(0));
		assertEquals(ResultatImport.Statut.IMPORTEE,
				service().importer(lucie, 42L, Facture.Sens.ACHAT, "edf.pdf", PDF).statut());
	}

}
