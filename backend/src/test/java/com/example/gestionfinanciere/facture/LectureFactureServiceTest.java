package com.example.gestionfinanciere.facture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.HorlogeReglable;
import com.example.gestionfinanciere.dossier.Dossier;
import com.example.gestionfinanciere.facture.lecture.AnalyseurFacture;
import com.example.gestionfinanciere.facture.lecture.ExtracteurTexte;
import com.example.gestionfinanciere.facture.lecture.LanceurLecture;
import com.example.gestionfinanciere.facture.lecture.DemandeLecture;
import com.example.gestionfinanciere.facture.lecture.ResultatLecture;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.JournalCapture;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

class LectureFactureServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-20T09:00:00Z");
	private static final String EDF = """
			EDF
			Facture n° F-2026-0611 du 12/06/2026
			Total HT 155,33 €
			TVA 20 % 31,07 €
			Total TTC 186,40 €
			""";

	private final FactureRepository factures = mock(FactureRepository.class);
	private final ChampLuRepository champs = mock(ChampLuRepository.class);
	private final LectureFactureService service = new LectureFactureService(factures, champs,
			new HorlogeReglable(MAINTENANT));

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, MAINTENANT, MAINTENANT);
	private final Compte jean = Compte.administrateurDe(cabinet, "jean@test.fr", "x", "Jean Dupont");
	private final Dossier dossier = new Dossier(cabinet, "Boulangerie Martin", "812345679", jean, MAINTENANT);

	private Facture facture(Facture.Sens sens) {
		Facture facture = new Facture(dossier, sens, "edf.pdf", "application/pdf", MAINTENANT, jean, new byte[] { 1 });
		ReflectionTestUtils.setField(facture, "id", 5L);
		return facture;
	}

	@BeforeEach
	void base() {
		ReflectionTestUtils.setField(dossier, "id", 42L);
	}

	// US-53 RG1 et US-54 RG1 : factures fournisseurs et factures clients sont lues ; jamais sans fichier.
	@Test
	void rg1FacturesFournisseursEtClients() {
		Facture achat = facture(Facture.Sens.ACHAT);
		assertTrue(achat.demarrerLecture());
		assertEquals(Facture.StatutLecture.EN_COURS, achat.getLectureStatut());

		Facture vente = facture(Facture.Sens.VENTE);
		assertTrue(vente.demarrerLecture());
		assertEquals(Facture.StatutLecture.EN_COURS, vente.getLectureStatut());

		Facture sansFichier = new Facture(dossier, Facture.Sens.ACHAT, "x.pdf", "application/pdf", MAINTENANT, jean,
				null);
		assertFalse(sansFichier.demarrerLecture());
	}

	@Test
	void fichierALire() {
		Facture enCours = facture(Facture.Sens.ACHAT);
		enCours.demarrerLecture();
		when(factures.findById(5L)).thenReturn(Optional.of(enCours));
		FactureALire aLire = service.factureALire(5L).orElseThrow();
		assertEquals("edf.pdf", aLire.fichier().nomFichier());
		assertEquals(Facture.Sens.ACHAT, aLire.sens());
		assertEquals("Boulangerie Martin", aLire.entrepriseDuDossier());

		// Déjà lue, jamais mise en lecture ou supprimée : rien à lire.
		enCours.terminerLecture(ResultatLecture.illisible(), MAINTENANT);
		assertTrue(service.factureALire(5L).isEmpty());
		when(factures.findById(6L)).thenReturn(Optional.of(facture(Facture.Sens.VENTE)));
		assertTrue(service.factureALire(6L).isEmpty());
		assertTrue(service.factureALire(7L).isEmpty());
	}

	// CA1, RG4 : champs enregistrés avec leur confiance ; log sans aucune valeur lue.
	@Test
	@SuppressWarnings("unchecked")
	void ca1LectureEnregistree() {
		Facture facture = facture(Facture.Sens.ACHAT);
		facture.demarrerLecture();
		when(factures.findById(5L)).thenReturn(Optional.of(facture));

		List<ILoggingEvent> logs;
		try (JournalCapture journal = JournalCapture.de(EvenementMetier.class)) {
			service.enregistrer(5L, AnalyseurFacture.analyser(EDF), Duration.ofMillis(120));
			logs = journal.lignes();
		}

		assertEquals(Facture.StatutLecture.LUE, facture.getLectureStatut());
		assertEquals(MAINTENANT, facture.getLectureTermineeLe());
		assertTrue(facture.isLectureAVerifier());
		assertEquals("EDF", facture.getNomTiers());
		assertEquals("FOURNISSEUR", facture.getTypeTiers());
		assertEquals("F-2026-0611", facture.getNumeroFacture());
		assertEquals(LocalDate.of(2026, 6, 12), facture.getDateEmission());
		assertEquals(new BigDecimal("155.33"), facture.getMontantHt());
		assertEquals(new BigDecimal("31.07"), facture.getMontantTva());
		assertEquals(new BigDecimal("20.00"), facture.getTauxTva());
		assertEquals(new BigDecimal("186.40"), facture.getMontantTtc());
		assertEquals("6061", facture.getCompteContrepartie());
		verify(champs).supprimerDeLaFacture(5L);
		ArgumentCaptor<List<ChampLu>> lus = ArgumentCaptor.forClass(List.class);
		verify(champs).saveAll(lus.capture());
		assertEquals(9, lus.getValue().size());
		ChampLu contrepartie = lus.getValue().stream()
				.filter(c -> c.getChamp() == ChampFacture.COMPTE_CONTREPARTIE).findFirst().orElseThrow();
		assertTrue(contrepartie.isAVerifier());
		assertEquals(new BigDecimal("0.700"), contrepartie.getConfiance());
		assertEquals(facture, contrepartie.getFacture());
		assertNull(contrepartie.getId());

		assertEquals(1, logs.size());
		assertEquals(Level.INFO, logs.get(0).getLevel());
		String log = logs.get(0).getFormattedMessage() + JournalCapture.champs(logs.get(0));
		assertTrue(log.contains("facture.lecture"));
		assertFalse(log.contains("EDF") || log.contains("186") || log.contains("F-2026"));

		// Page de la facture : valeurs, libellé du compte, champs à vérifier.
		VueFacture.Lecture vue = VueFacture.de(facture, lus.getValue()).lecture();
		assertEquals(Facture.StatutLecture.LUE, vue.statut());
		assertEquals("Fournitures non stockables (eau, énergie)", vue.libelleContrepartie());
		assertEquals(List.of(ChampFacture.COMPTE_CONTREPARTIE), vue.aVerifier());
		assertEquals(List.of(), vue.aRenseigner());
	}

	// CA2 / RG3 : tiers non lu -> vide, « À renseigner » sur la page de la facture.
	@Test
	void ca2TiersARenseigner() {
		Facture facture = facture(Facture.Sens.ACHAT);
		facture.demarrerLecture();
		facture.terminerLecture(AnalyseurFacture.analyser("TOTAL TTC 21,40\n"), MAINTENANT);

		VueFacture.Lecture vue = VueFacture.de(facture, List.of()).lecture();
		assertNull(vue.nomTiers());
		assertTrue(vue.aRenseigner().containsAll(List.of(ChampFacture.NOM_TIERS, ChampFacture.MONTANT_HT,
				ChampFacture.NUMERO_FACTURE, ChampFacture.DATE_EMISSION, ChampFacture.MONTANT_TVA,
				ChampFacture.TAUX_TVA)));
		assertFalse(vue.aRenseigner().contains(ChampFacture.MONTANT_TTC));
	}

	// CA4 : « Lecture échouée » (log en avertissement), aucun champ ; facture supprimée entre-temps : rien.
	@Test
	void ca4LectureEchouee() {
		Facture facture = facture(Facture.Sens.ACHAT);
		facture.demarrerLecture();
		when(factures.findById(5L)).thenReturn(Optional.of(facture));

		List<ILoggingEvent> logs;
		try (JournalCapture journal = JournalCapture.de(EvenementMetier.class)) {
			service.enregistrer(5L, ResultatLecture.illisible(), Duration.ZERO);
			logs = journal.lignes();
		}

		assertEquals(Facture.StatutLecture.ECHOUEE, facture.getLectureStatut());
		assertFalse(facture.isLectureAVerifier());
		assertNull(facture.getMontantTtc());
		assertEquals(Level.WARN, logs.get(0).getLevel());
		VueFacture.Lecture vue = VueFacture.de(facture, List.of()).lecture();
		assertEquals(Facture.StatutLecture.ECHOUEE, vue.statut());
		assertNull(vue.montantTtc());

		service.enregistrer(8L, ResultatLecture.illisible(), Duration.ZERO);
		verify(champs, never()).supprimerDeLaFacture(8L);
		// Jamais lue (facture déposée avant l'US-53) : pas de bloc lecture.
		assertNull(VueFacture.de(facture(Facture.Sens.VENTE), List.of()).lecture());
	}

	// RG6 : le lanceur lit le fichier puis enregistre ; toute erreur donne « Lecture échouée ».
	@Test
	void lanceur() {
		LectureFactureService lectures = mock(LectureFactureService.class);
		ExtracteurTexte extracteur = mock(ExtracteurTexte.class);
		LanceurLecture lanceur = new LanceurLecture(lectures, extracteur);
		FichierFacture pdf = new FichierFacture("edf.pdf", "application/pdf", new byte[] { 1 });
		FactureALire fichier = new FactureALire(pdf, Facture.Sens.ACHAT, "Boulangerie Martin");
		when(lectures.factureALire(5L)).thenReturn(Optional.of(fichier));
		when(extracteur.texte(any(), any())).thenReturn(EDF);

		lanceur.lancer(new DemandeLecture(5L));
		ArgumentCaptor<ResultatLecture> resultat = ArgumentCaptor.forClass(ResultatLecture.class);
		verify(lectures).enregistrer(org.mockito.ArgumentMatchers.eq(5L), resultat.capture(), any());
		assertTrue(resultat.getValue().lisible());
		assertEquals("FOURNISSEUR", resultat.getValue().typeTiers());

		// US-54 : facture client lue comme telle.
		when(lectures.factureALire(9L))
				.thenReturn(Optional.of(new FactureALire(pdf, Facture.Sens.VENTE, "Boulangerie Martin")));
		lanceur.lancer(new DemandeLecture(9L));
		verify(lectures).enregistrer(org.mockito.ArgumentMatchers.eq(9L), resultat.capture(), any());
		assertEquals("CLIENT", resultat.getValue().typeTiers());

		when(lectures.factureALire(6L)).thenReturn(Optional.of(fichier));
		when(extracteur.texte(any(), any())).thenThrow(new IllegalStateException("panne"));
		lanceur.lancer(new DemandeLecture(6L));
		verify(lectures).enregistrer(org.mockito.ArgumentMatchers.eq(6L), resultat.capture(), any());
		assertFalse(resultat.getValue().lisible());

		when(lectures.factureALire(7L)).thenReturn(Optional.empty());
		lanceur.lancer(new DemandeLecture(7L));
		verify(lectures, never()).enregistrer(org.mockito.ArgumentMatchers.eq(7L), any(), any());
	}

	// US-54 CA2 : plusieurs taux : « Multiple », détail par taux enregistré puis remplacé à la lecture suivante.
	@Test
	void us54Ca2DetailTvaEnregistre() {
		Facture facture = facture(Facture.Sens.VENTE);
		facture.demarrerLecture();
		when(factures.findById(5L)).thenReturn(Optional.of(facture));

		service.enregistrer(5L, AnalyseurFacture.analyserFactureClient("""
				Facture n° F-2026-020 du 10/06/2026
				Client : Mairie de Lyon
				TVA 20 % sur 100,00 : 20,00
				TVA 5,5 % sur 50,00 : 2,75
				Total HT 150,00
				Total TVA 22,75
				Total TTC 172,75
				""", "Boulangerie Martin"), Duration.ZERO);

		VueFacture.Lecture vue = VueFacture.de(facture, List.of()).lecture();
		assertEquals("CLIENT", vue.typeTiers());
		assertEquals("Mairie de Lyon", vue.nomTiers());
		assertTrue(vue.tauxMultiple());
		assertNull(vue.tauxTva());
		assertEquals(2, vue.detailTva().size());
		assertEquals(new BigDecimal("5.50"), vue.detailTva().get(1).taux());
		assertFalse(vue.aRenseigner().contains(ChampFacture.TAUX_TVA));

		facture.terminerLecture(ResultatLecture.illisible(), MAINTENANT);
		assertFalse(facture.isTauxTvaMultiple());
		assertTrue(facture.getDetailTva().isEmpty());
	}
}
