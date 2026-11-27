package com.example.gestionfinanciere.ecriture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.plancomptable.CompteComptable;
import com.example.gestionfinanciere.plancomptable.CompteComptableRepository;

// US-56 Enregistrement et affichage de l'écriture ; refus d'une écriture déséquilibrée ou hors plan.
class EcritureServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-26T09:00:00Z");
	private static final Instant PLUS_TARD = Instant.parse("2026-11-26T10:00:00Z");

	private final EcritureRepository ecritures = mock(EcritureRepository.class);
	private final CompteComptableRepository comptes = mock(CompteComptableRepository.class);
	private final EcritureService service = new EcritureService(ecritures, comptes);
	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, MAINTENANT, MAINTENANT);
	private final Compte lucie = Compte.administrateurDe(cabinet, "lucie@cabinet-test.fr", "x", "Lucie Moreau");

	private static CalculEcriture.Resultat edf(String ht, String ttc) {
		return CalculEcriture.achat(new PieceAchat("EDF", "F-1", LocalDate.of(2026, 6, 12), new BigDecimal(ht),
				List.of(new PieceAchat.TvaDuTaux(new BigDecimal("20"), new BigDecimal("31.07"))), new BigDecimal(ttc),
				"607"));
	}

	@Test
	void refus() {
		assertEquals(Optional.of(EcritureService.MESSAGE_DESEQUILIBREE), service.refus(42L, edf("100", "186.40")));

		when(comptes.findByDossierIdAndNumero(42L, "607"))
				.thenReturn(Optional.of(CompteComptable.general(42L, "607", "Achats de marchandises", MAINTENANT)));
		assertEquals(Optional.of("Le compte 44566 n'est pas dans le plan comptable du dossier"),
				service.refus(42L, edf("155.33", "186.40")));

		when(comptes.findByDossierIdAndNumero(42L, "44566"))
				.thenReturn(Optional.of(CompteComptable.general(42L, "44566", "TVA", MAINTENANT)));
		when(comptes.findByDossierIdAndNumero(42L, "401"))
				.thenReturn(Optional.of(CompteComptable.general(42L, "401", "Fournisseurs", MAINTENANT)));
		assertTrue(service.refus(42L, edf("155.33", "186.40")).isEmpty());
	}

	// RG1 puis RG5 : la même écriture est recalculée, ses lignes remplacées.
	@Test
	void enregistrerPuisRegenerer() {
		when(ecritures.save(any(Ecriture.class))).thenAnswer(appel -> appel.getArgument(0));
		Ecriture creee = service.enregistrer(42L, 5L, Ecriture.Journal.AC, edf("155.33", "186.40"), lucie, MAINTENANT);
		assertEquals(Ecriture.Journal.AC, creee.getJournal());
		assertEquals("EDF F-1", creee.getLibelle());
		assertEquals(3, creee.getLignes().size());
		assertNull(creee.getId());
		assertFalse(creee.isExportee());
		assertNull(creee.getExporteeLe());

		when(ecritures.findByFactureId(5L)).thenReturn(Optional.of(creee));
		Ecriture regeneree = service.enregistrer(42L, 5L, Ecriture.Journal.AC, edf("160.00", "191.07"), lucie,
				PLUS_TARD);
		assertSame(creee, regeneree);
		assertEquals(PLUS_TARD, regeneree.getGenereeLe());
		assertEquals(lucie, regeneree.getGenereePar());
		assertEquals(3, regeneree.getLignes().size());
		LigneEcriture premiere = regeneree.getLignes().get(0);
		assertEquals(1, premiere.getOrdre());
		assertEquals("607", premiere.getNumeroCompte());
		assertEquals("EDF F-1", premiere.getLibelle());
		assertEquals(new BigDecimal("160.00"), premiere.getDebit());
		assertEquals(new BigDecimal("0.00"), premiere.getCredit());
		assertEquals(Optional.of(creee), service.deLaFacture(5L));
	}

	// RG6 : libellés des comptes du plan du dossier ; « à jour » si le calcul actuel est le même.
	@Test
	void vue() {
		Ecriture ecriture = Ecriture.achat(42L, 5L);
		ecriture.remplir(edf("155.33", "186.40"), lucie, MAINTENANT);
		when(comptes.findByDossierIdOrderByNumero(42L)).thenReturn(
				List.of(CompteComptable.general(42L, "607", "Achats de marchandises", MAINTENANT)));

		VueEcriture vue = service.vue(ecriture, edf("155.33", "186.4"));
		assertTrue(vue.aJour());
		assertEquals("Achats de marchandises", vue.lignes().get(0).libelleCompte());
		assertNull(vue.lignes().get(1).libelleCompte());
		assertEquals("Lucie Moreau", vue.genereePar());
		assertEquals(new BigDecimal("186.40"), vue.totalCredit());
		assertEquals(42L, ecriture.getDossierId());
		assertEquals(5L, ecriture.getFactureId());
		assertEquals(LocalDate.of(2026, 6, 12), ecriture.getDateEcriture());

		assertFalse(service.vue(ecriture, edf("160.00", "191.07")).aJour());
		assertFalse(service.vue(ecriture, null).aJour());
	}

}
