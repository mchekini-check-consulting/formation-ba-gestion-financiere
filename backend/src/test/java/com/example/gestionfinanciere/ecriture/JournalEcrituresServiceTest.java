package com.example.gestionfinanciere.ecriture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.dossier.Dossier;
import com.example.gestionfinanciere.dossier.DossierRepository;
import com.example.gestionfinanciere.ecriture.CorrectionEcriture.LigneCorrigee;
import com.example.gestionfinanciere.ecriture.ResultatEcriture.Statut;
import com.example.gestionfinanciere.plancomptable.CompteComptable;
import com.example.gestionfinanciere.plancomptable.CompteComptableRepository;

// US-47 Journal d'un dossier (RG1) et correction d'une écriture (RG2 à RG4, CA1 à CA3).
class JournalEcrituresServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-06-12T09:00:00Z");
	private static final Instant CORRECTION = Instant.parse("2026-06-12T10:00:00Z");

	private final EcritureRepository ecritures = mock(EcritureRepository.class);
	private final CompteComptableRepository comptes = mock(CompteComptableRepository.class);
	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final JournalEcrituresService journal = new JournalEcrituresService(ecritures,
			new EcritureService(ecritures, comptes), dossiers, Clock.fixed(CORRECTION, ZoneOffset.UTC));
	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, MAINTENANT, MAINTENANT);
	private final Compte lucie = Compte.administrateurDe(cabinet, "lucie@cabinet-test.fr", "x", "Lucie Moreau");
	private final List<CompteComptable> plan = new ArrayList<>();
	private Ecriture edf;

	@BeforeEach
	void dossier() {
		when(dossiers.findByIdAndCabinetId(eq(42L), any())).thenReturn(Optional.of(mock(Dossier.class)));
		compte("401", "Fournisseurs");
		compte("44566", "TVA déductible sur autres biens et services");
		compte("607", "Achats de marchandises");
		compte("6061", "Fournitures non stockables (eau, énergie)");
		when(comptes.findByDossierIdOrderByNumero(42L)).thenReturn(plan);
		edf = ecriture(5L, 7L, "EDF", "F-2026-0611", LocalDate.of(2026, 6, 12));
		when(ecritures.findByIdAndDossierId(7L, 42L)).thenReturn(Optional.of(edf));
		when(ecritures.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	private void compte(String numero, String libelle) {
		CompteComptable compte = CompteComptable.general(42L, numero, libelle, MAINTENANT);
		plan.add(compte);
		when(comptes.findByDossierIdAndNumero(42L, numero)).thenReturn(Optional.of(compte));
	}

	private Ecriture ecriture(Long factureId, Long id, String tiers, String numero, LocalDate date) {
		Ecriture ecriture = Ecriture.achat(42L, factureId);
		ecriture.remplir(CalculEcriture.achat(new PieceAchat(tiers, numero, date, new BigDecimal("155.33"),
				List.of(new PieceAchat.TvaDuTaux(new BigDecimal("20"), new BigDecimal("31.07"))),
				new BigDecimal("186.40"), "607")), lucie, MAINTENANT);
		ReflectionTestUtils.setField(ecriture, "id", id);
		return ecriture;
	}

	private static CorrectionEcriture correction(String libelle, String compte, String debit, String credit) {
		return new CorrectionEcriture(libelle, List.of(new LigneCorrigee(compte, new BigDecimal(debit), null),
				new LigneCorrigee("44566", new BigDecimal("31.07"), BigDecimal.ZERO),
				new LigneCorrigee("401", null, new BigDecimal(credit))));
	}

	// RG1 : périodes proposées (mois récents d'abord, exercice après ses mois), journal du dernier mois.
	@Test
	void consulter() {
		when(ecritures.datesDuDossier(42L)).thenReturn(
				List.of(LocalDate.of(2026, 6, 12), LocalDate.of(2026, 5, 3), LocalDate.of(2025, 12, 30)));
		Ecriture orange = ecriture(6L, 8L, "Orange", "OR-1", LocalDate.of(2026, 6, 26));
		when(ecritures.findByDossierIdAndDateEcritureBetweenOrderByDateEcritureAscIdAsc(42L, LocalDate.of(2026, 6, 1),
				LocalDate.of(2026, 6, 30))).thenReturn(List.of(edf, orange));

		JournalEcritures juin = journal.consulter(lucie, 42L, null).orElseThrow();
		assertEquals("2026-06", juin.periode());
		assertEquals(List.of(new JournalEcritures.Periode("2026-06", "Juin 2026"),
				new JournalEcritures.Periode("2026-05", "Mai 2026"),
				new JournalEcritures.Periode("2026", "Exercice 2026"),
				new JournalEcritures.Periode("2025-12", "Décembre 2025"),
				new JournalEcritures.Periode("2025", "Exercice 2025")), juin.periodes());
		assertEquals(2, juin.ecritures().size());
		JournalEcritures.Ligne premiere = juin.ecritures().get(0);
		assertEquals(7L, premiere.id());
		assertEquals("F-2026-0611", premiere.piece());
		assertEquals("EDF F-2026-0611", premiere.libelle());
		assertEquals("Achats de marchandises", premiere.lignes().get(0).libelleCompte());
		assertFalse(premiere.exportee());
		assertNull(premiere.modifieePar());
		assertEquals(new BigDecimal("372.80"), juin.totalDebit());
		assertEquals(new BigDecimal("372.80"), juin.totalCredit());

		// Exercice : toute l'année ; période inconnue : dernier mois.
		when(ecritures.findByDossierIdAndDateEcritureBetweenOrderByDateEcritureAscIdAsc(42L, LocalDate.of(2026, 1, 1),
				LocalDate.of(2026, 12, 31))).thenReturn(List.of(edf));
		assertEquals(1, journal.consulter(lucie, 42L, "2026").orElseThrow().ecritures().size());
		assertEquals("2026-06", journal.consulter(lucie, 42L, "2024-01").orElseThrow().periode());
	}

	@Test
	void consulterSansEcritureOuDossierInaccessible() {
		when(ecritures.datesDuDossier(42L)).thenReturn(List.of());
		JournalEcritures vide = journal.consulter(lucie, 42L, null).orElseThrow();
		assertNull(vide.periode());
		assertTrue(vide.ecritures().isEmpty());
		assertEquals(BigDecimal.ZERO, vide.totalDebit());

		assertTrue(journal.consulter(lucie, 99L, null).isEmpty());
	}

	// CA1, RG4 : 607 remplacé par 6061 ; auteur et date gardés ; les libellés des lignes suivent.
	@Test
	void ca1Correction() {
		ResultatEcriture resultat = journal.modifier(lucie, 42L, 7L,
				correction("EDF – facture F-2026-0611", " 6061 ", "155.33", "186.40"));

		assertEquals(Statut.MODIFIEE, resultat.statut());
		JournalEcritures.Ligne vue = resultat.ecriture();
		assertEquals("Lucie Moreau", vue.modifieePar());
		assertEquals(CORRECTION, vue.modifieeLe());
		assertEquals("EDF – facture F-2026-0611", vue.libelle());
		assertEquals("6061", vue.lignes().get(0).numeroCompte());
		assertEquals("Fournitures non stockables (eau, énergie)", vue.lignes().get(0).libelleCompte());
		assertEquals("EDF – facture F-2026-0611 – TVA 20 %", vue.lignes().get(1).libelle());
		assertEquals(new BigDecimal("186.40"), vue.totalCredit());
		assertTrue(edf.isModifiee());
		verify(ecritures).save(edf);
	}

	// CA2 : déséquilibre, rien n'est enregistré.
	@Test
	void ca2Desequilibre() {
		ResultatEcriture resultat = journal.modifier(lucie, 42L, 7L,
				correction("EDF F-2026-0611", "607", "165.33", "186.40"));

		assertEquals(Statut.INVALIDE, resultat.statut());
		assertEquals("Écriture déséquilibrée : écart de 10,00 €", resultat.message());
		assertFalse(edf.isModifiee());
		assertEquals("607", edf.getLignes().get(0).getNumeroCompte());
		verify(ecritures, never()).save(any());
	}

	// RG3 : compte absent ou désactivé du plan.
	@Test
	void rg3ComptesDuPlan() {
		assertEquals("Le compte 6064 n'est pas dans le plan comptable du dossier",
				journal.modifier(lucie, 42L, 7L, correction("EDF", "6064", "155.33", "186.40")).message());

		plan.get(3).setActif(false);
		assertEquals("Le compte 6061 est désactivé dans le plan comptable du dossier",
				journal.modifier(lucie, 42L, 7L, correction("EDF", "6061", "155.33", "186.40")).message());
		verify(ecritures, never()).save(any());
	}

	// CA3 : écriture exportée, en lecture seule.
	@Test
	void ca3Exportee() {
		ReflectionTestUtils.setField(edf, "exporteeLe", MAINTENANT);

		ResultatEcriture resultat = journal.modifier(lucie, 42L, 7L, correction("EDF", "6061", "155.33", "186.40"));
		assertEquals(Statut.EXPORTEE, resultat.statut());
		assertEquals("Écriture exportée : elle ne se modifie plus", resultat.message());
		verify(ecritures, never()).save(any());
	}

	@Test
	void formulaireInvalide() {
		assertEquals(JournalEcrituresService.MESSAGE_LIBELLE, message(correction(" ", "607", "155.33", "186.40")));
		assertEquals(JournalEcrituresService.MESSAGE_LIBELLE, message(null));
		assertEquals(JournalEcrituresService.MESSAGE_LIBELLE_LONG,
				message(correction("x".repeat(401), "607", "155.33", "186.40")));
		assertEquals(JournalEcrituresService.MESSAGE_NOMBRE_LIGNES, message(new CorrectionEcriture("EDF", null)));
		assertEquals(JournalEcrituresService.MESSAGE_NOMBRE_LIGNES, message(new CorrectionEcriture("EDF",
				List.of(new LigneCorrigee("607", BigDecimal.ONE, null)))));
		assertEquals(JournalEcrituresService.MESSAGE_COMPTE, message(correction("EDF", " ", "155.33", "186.40")));
		assertEquals(JournalEcrituresService.MESSAGE_MONTANT, message(correction("EDF", "607", "-1", "186.40")));
		assertEquals(JournalEcrituresService.MESSAGE_MONTANT, message(correction("EDF", "607", "0", "186.40")));
		assertEquals(JournalEcrituresService.MESSAGE_MONTANT, message(correction("EDF", "607", "155.333", "186.40")));
		assertEquals(JournalEcrituresService.MESSAGE_MONTANT,
				message(correction("EDF", "607", "155.33", "99999999999")));
		assertEquals(JournalEcrituresService.MESSAGE_MONTANT, message(new CorrectionEcriture("EDF",
				List.of(new LigneCorrigee("607", BigDecimal.ONE, BigDecimal.ONE),
						new LigneCorrigee("44566", BigDecimal.ONE, null), new LigneCorrigee("401", null, BigDecimal.ONE)))));
		verify(ecritures, never()).save(any());
	}

	private String message(CorrectionEcriture correction) {
		return journal.modifier(lucie, 42L, 7L, correction).message();
	}

	@Test
	void introuvable() {
		assertEquals(Statut.DOSSIER_INTROUVABLE,
				journal.modifier(lucie, 99L, 7L, correction("EDF", "607", "155.33", "186.40")).statut());
		assertEquals(Statut.ECRITURE_INTROUVABLE,
				journal.modifier(lucie, 42L, 8L, correction("EDF", "607", "155.33", "186.40")).statut());
	}

	@Test
	void euros() {
		String euros = JournalEcrituresService.euros(new BigDecimal("1234.5"));
		assertEquals("1 234,50 €", euros.replace('\u00a0', ' ').replace('\u202f', ' '));
	}

}
