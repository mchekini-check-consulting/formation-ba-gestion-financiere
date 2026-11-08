package com.example.gestionfinanciere.dossier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.cabinet.ValidationInscription;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.HorlogeReglable;
import com.example.gestionfinanciere.logging.JournalCapture;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

class DossierServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-08T09:00:00Z");

	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final DossierService service = new DossierService(dossiers, new HorlogeReglable(MAINTENANT));

	private final Cabinet cabinetTest = new Cabinet("Cabinet Test", "123456789", 120, null, MAINTENANT, MAINTENANT);
	private final Compte jean = Compte.administrateurDe(cabinetTest, "jean.dupont@cabinet-test.fr", "x",
			"Jean Dupont");

	// CA1 : Jean crée le dossier de Fleurs Martin EURL.
	static DemandeDossier fleursMartin() {
		return new DemandeDossier("  Fleurs Martin EURL ", "812 345 679");
	}

	@BeforeEach
	void base() {
		ReflectionTestUtils.setField(cabinetTest, "id", 7L);
		when(dossiers.save(any(Dossier.class))).thenAnswer(i -> {
			Dossier dossier = i.getArgument(0);
			ReflectionTestUtils.setField(dossier, "id", 42L);
			return dossier;
		});
	}

	@Test
	void ca1DossierCreeDansLeCabinetDuCollaborateur() {
		ResultatCreationDossier resultat = service.creer(jean, fleursMartin());

		assertEquals(ResultatCreationDossier.Statut.CREE, resultat.statut());
		assertEquals(new VueDossier(42L, "Fleurs Martin EURL", "812345679", MAINTENANT), resultat.dossier());
		ArgumentCaptor<Dossier> enregistre = ArgumentCaptor.forClass(Dossier.class);
		verify(dossiers).save(enregistre.capture());
		assertSame(cabinetTest, enregistre.getValue().getCabinet());
		assertSame(jean, enregistre.getValue().getCreePar());
	}

	@Test
	void ca2SirenDejaSuiviParLeCabinet() {
		when(dossiers.existsByCabinetIdAndSiren(7L, "812345679")).thenReturn(true);

		ResultatCreationDossier resultat = service.creer(jean, fleursMartin());

		assertEquals(ResultatCreationDossier.Statut.SIREN_DEJA_UTILISE, resultat.statut());
		assertNull(resultat.dossier());
		verify(dossiers, never()).save(any());
	}

	@Test
	void rg2DoublonCreeEnMemeTempsRefuseParLaBase() {
		when(dossiers.save(any(Dossier.class))).thenThrow(new DataIntegrityViolationException("unique"));

		assertEquals(ResultatCreationDossier.Statut.SIREN_DEJA_UTILISE, service.creer(jean, fleursMartin()).statut());
	}

	@Test
	void rg1RaisonSocialeEtSirenObligatoires() {
		ResultatCreationDossier resultat = service.creer(jean, new DemandeDossier(" ", null));

		assertEquals(ResultatCreationDossier.Statut.INVALIDE, resultat.statut());
		assertEquals(Map.of("raisonSociale", ValidationInscription.OBLIGATOIRE, "siren",
				ValidationInscription.OBLIGATOIRE), resultat.erreurs());
		verify(dossiers, never()).save(any());
	}

	@Test
	void rg3DossierOuvertSeulementDansSonCabinet() {
		Dossier dossier = new Dossier(cabinetTest, "Fleurs Martin EURL", "812345679", jean, MAINTENANT);
		ReflectionTestUtils.setField(dossier, "id", 42L);
		when(dossiers.findByIdAndCabinetId(42L, 7L)).thenReturn(Optional.of(dossier));
		when(dossiers.findByIdAndCabinetId(43L, 7L)).thenReturn(Optional.empty());

		assertEquals(Optional.of(new VueDossier(42L, "Fleurs Martin EURL", "812345679", MAINTENANT)),
				service.consulter(jean, 42L));
		assertTrue(service.consulter(jean, 43L).isEmpty());
	}

	// Logs : création (info, numéro du dossier seulement : ni raison sociale ni SIREN).
	@Test
	void logDossierCree() {
		try (JournalCapture journal = JournalCapture.de(DossierService.class)) {
			service.creer(jean, fleursMartin());

			ILoggingEvent ligne = journal.seule(Level.INFO);
			assertEquals("dossier créé", ligne.getFormattedMessage());
			assertEquals(Map.of("dossier", "42"), JournalCapture.champs(ligne));
		}
	}

	@Test
	void logDoublonEnInfo() {
		when(dossiers.existsByCabinetIdAndSiren(7L, "812345679")).thenReturn(true);
		try (JournalCapture journal = JournalCapture.de(DossierService.class)) {
			service.creer(jean, fleursMartin());

			assertEquals("création de dossier refusée : SIREN déjà suivi par le cabinet",
					journal.seule(Level.INFO).getFormattedMessage());
		}
	}

	// Logs : ouverture (info) ; dossier d'un autre cabinet (warn, refus d'accès).
	@Test
	void logOuvertureEtRefusDAcces() {
		Dossier dossier = new Dossier(cabinetTest, "Fleurs Martin EURL", "812345679", jean, MAINTENANT);
		ReflectionTestUtils.setField(dossier, "id", 42L);
		when(dossiers.findByIdAndCabinetId(42L, 7L)).thenReturn(Optional.of(dossier));
		try (JournalCapture journal = JournalCapture.de(DossierService.class)) {
			service.consulter(jean, 42L);
			service.consulter(jean, 99L);

			assertEquals("dossier ouvert", journal.seule(Level.INFO).getFormattedMessage());
			ILoggingEvent refus = journal.seule(Level.WARN);
			assertEquals("accès refusé : dossier introuvable ou d'un autre cabinet", refus.getFormattedMessage());
			assertEquals(Map.of("dossier", "99"), JournalCapture.champs(refus));
		}
	}

}
