package com.example.gestionfinanciere.facture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
import com.example.gestionfinanciere.dossier.DossierRepository;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.JournalCapture;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

class DepotFactureServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-11T09:00:00Z");
	private static final byte[] PDF = "%PDF-1.7 facture".getBytes(StandardCharsets.US_ASCII);

	private final FactureRepository factures = mock(FactureRepository.class);
	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final DepotFactureService service = new DepotFactureService(factures, dossiers,
			new HorlogeReglable(MAINTENANT));

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, MAINTENANT, MAINTENANT);
	private final Compte jean = Compte.administrateurDe(cabinet, "jean@test.fr", "x", "Jean Dupont");
	private final Dossier boulangerie = new Dossier(cabinet, "Boulangerie Martin", "812345679", jean, MAINTENANT);
	private final Compte sophie = Compte.dirigeantInvite(42L, "sophie@test.fr", "x", "Sophie Martin");

	@BeforeEach
	void base() {
		ReflectionTestUtils.setField(sophie, "id", 9L);
		ReflectionTestUtils.setField(boulangerie, "id", 42L);
		when(dossiers.findById(42L)).thenReturn(Optional.of(boulangerie));
		when(factures.save(any(Facture.class))).thenAnswer(i -> {
			Facture facture = i.getArgument(0);
			ReflectionTestUtils.setField(facture, "id", 5L);
			return facture;
		});
	}

	private Facture depot(String nom, Instant ouverteLe) {
		Facture facture = new Facture(boulangerie, Facture.Sens.ACHAT, nom, TypeFichier.PDF, MAINTENANT, sophie, PDF);
		ReflectionTestUtils.setField(facture, "id", 5L);
		if (ouverteLe != null) {
			facture.marquerOuverte(ouverteLe);
		}
		return facture;
	}

	// US-15 CA1 : la facture est rangée dans le dossier de Sophie, onglet Factures fournisseurs (RG3).
	@Test
	void us15Ca1FactureFournisseurDeposee() {
		ResultatDepot resultat = service.deposer(sophie, Facture.Sens.ACHAT, "facture-moulins-bertrand-0142.pdf", PDF);

		assertEquals(ResultatDepot.Statut.DEPOSEE, resultat.statut());
		assertEquals(new FactureDeposee(5L, "facture-moulins-bertrand-0142.pdf", MAINTENANT, Facture.Sens.ACHAT, true),
				resultat.facture());
		ArgumentCaptor<Facture> enregistree = ArgumentCaptor.forClass(Facture.class);
		verify(factures).save(enregistree.capture());
		Facture facture = enregistree.getValue();
		assertSame(boulangerie, facture.getDossier());
		assertSame(sophie, facture.getDeposeePar());
		assertEquals(TypeFichier.PDF, facture.getTypeFichier());
		// Badge « Nouvelle » côté cabinet (US-12 RG3).
		assertNull(facture.getOuverteLe());
	}

	// US-38 CA1 : facture client rangée dans les ventes seulement.
	@Test
	void us38Ca1FactureClientDansLesVentes() {
		service.deposer(sophie, Facture.Sens.VENTE, "facture-client-FC-2026-031.pdf", PDF);

		ArgumentCaptor<Facture> enregistree = ArgumentCaptor.forClass(Facture.class);
		verify(factures).save(enregistree.capture());
		assertEquals(Facture.Sens.VENTE, enregistree.getValue().getSens());
	}

	// US-15 CA2 / RG1 : le vrai type compte, pas le nom ; plus de 10 Mo refusé.
	@Test
	void us15Ca2FormatEtTaille() {
		assertEquals(ResultatDepot.Statut.FORMAT_REFUSE,
				service.deposer(sophie, Facture.Sens.ACHAT, "releve.xlsx", new byte[] { 'P', 'K', 3, 4 }).statut());
		assertEquals(ResultatDepot.Statut.FORMAT_REFUSE,
				service.deposer(sophie, Facture.Sens.ACHAT, "faux.pdf", "bonjour".getBytes(StandardCharsets.UTF_8))
						.statut());
		assertEquals(ResultatDepot.Statut.FORMAT_REFUSE, service.deposer(sophie, Facture.Sens.ACHAT, "vide.pdf", null)
				.statut());
		byte[] gros = new byte[(int) DepotFactureService.TAILLE_MAX + 1];
		System.arraycopy(PDF, 0, gros, 0, PDF.length);
		assertEquals(ResultatDepot.Statut.TROP_VOLUMINEUX,
				service.deposer(sophie, Facture.Sens.ACHAT, "gros.pdf", gros).statut());
		verify(factures, never()).save(any());
	}

	@Test
	void dossierDuDirigeantDisparu() {
		when(dossiers.findById(42L)).thenReturn(Optional.empty());
		assertEquals(ResultatDepot.Statut.FORMAT_REFUSE,
				service.deposer(sophie, Facture.Sens.ACHAT, "facture.pdf", PDF).statut());
	}

	// Nom affiché : sans chemin, sans caractères de contrôle, jamais vide, 255 caractères au plus.
	@Test
	void nomDuFichier() {
		assertEquals("facture.pdf", DepotFactureService.nom("C:\\Users\\sophie\\facture.pdf"));
		assertEquals("facture.pdf", DepotFactureService.nom("../../facture.pdf"));
		assertEquals("ab.pdf", DepotFactureService.nom(" a\nb.pdf "));
		assertEquals("facture", DepotFactureService.nom(null));
		assertEquals("facture", DepotFactureService.nom("  "));
		assertEquals(255, DepotFactureService.nom("a".repeat(300)).length());
	}

	// US-16 RG1 : les dépôts de Sophie dans l'onglet, avec la date ; supprimable tant que non ouverte.
	@Test
	void us16Rg1ListeDesDepots() {
		when(factures.findByDossierIdAndSensAndDeposeeParIdOrderByDeposeeLeDescIdDesc(42L, Facture.Sens.ACHAT, 9L))
				.thenReturn(List.of(depot("ticket-carburant.jpg", null), depot("loyer.pdf", MAINTENANT)));

		List<FactureDeposee> liste = service.lister(sophie, Facture.Sens.ACHAT);

		assertEquals(List.of("ticket-carburant.jpg", "loyer.pdf"), liste.stream().map(FactureDeposee::nomFichier).toList());
		assertEquals(List.of(true, false), liste.stream().map(FactureDeposee::supprimable).toList());
	}

	// US-16 CA1 : facture jamais ouverte -> supprimée.
	@Test
	void us16Ca1Suppression() {
		Facture ticket = depot("ticket-carburant.jpg", null);
		when(factures.findByIdAndDossierIdAndDeposeeParId(5L, 42L, 9L)).thenReturn(Optional.of(ticket));

		assertEquals(DepotFactureService.ResultatSuppression.SUPPRIMEE, service.supprimer(sophie, 5L));
		verify(factures).delete(ticket);
	}

	// US-16 CA2 / RG3 : déjà ouverte par Lucie -> pas supprimée ; facture d'un autre -> introuvable.
	@Test
	void us16Ca2DejaOuverteOuIntrouvable() {
		when(factures.findByIdAndDossierIdAndDeposeeParId(5L, 42L, 9L))
				.thenReturn(Optional.of(depot("ticket.jpg", MAINTENANT)));

		assertEquals(DepotFactureService.ResultatSuppression.DEJA_OUVERTE, service.supprimer(sophie, 5L));
		assertEquals(DepotFactureService.ResultatSuppression.INTROUVABLE, service.supprimer(sophie, 6L));
		verify(factures, never()).delete(any());
	}

	// Logs métier : jamais le nom du fichier ; refus en warn.
	@Test
	void logsSansNomDeFichier() {
		try (JournalCapture journal = JournalCapture.de(EvenementMetier.class)) {
			service.deposer(sophie, Facture.Sens.ACHAT, "facture-moulins-bertrand-0142.pdf", PDF);
			service.deposer(sophie, Facture.Sens.ACHAT, "releve.xlsx", new byte[] { 1 });

			ILoggingEvent depot = journal.seule(Level.INFO);
			assertEquals("espace_client.depot", depot.getMDCPropertyMap().get("event"));
			assertEquals("deposee", depot.getMDCPropertyMap().get("outcome"));
			assertEquals("5", depot.getMDCPropertyMap().get("factureId"));
			assertFalse(depot.getFormattedMessage().contains("moulins"));
			assertFalse(depot.getMDCPropertyMap().toString().contains("moulins"));
			assertEquals("format_refuse", journal.seule(Level.WARN).getMDCPropertyMap().get("outcome"));
		}
	}

}
