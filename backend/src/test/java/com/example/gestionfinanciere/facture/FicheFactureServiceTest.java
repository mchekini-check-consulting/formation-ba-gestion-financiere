package com.example.gestionfinanciere.facture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.HorlogeReglable;
import com.example.gestionfinanciere.dossier.Dossier;
import com.example.gestionfinanciere.dossier.DossierRepository;
import com.example.gestionfinanciere.facture.lecture.ResultatLecture;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.JournalCapture;
import com.example.gestionfinanciere.plancomptable.CompteComptable;
import com.example.gestionfinanciere.plancomptable.CompteComptableRepository;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

class FicheFactureServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-25T09:00:00Z");

	private final FactureRepository factures = mock(FactureRepository.class);
	private final ChampLuRepository champsLus = mock(ChampLuRepository.class);
	private final ModificationFactureRepository modifications = mock(ModificationFactureRepository.class);
	private final ChangementStatutFactureRepository historique = mock(ChangementStatutFactureRepository.class);
	private final CompteComptableRepository comptes = mock(CompteComptableRepository.class);
	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final FicheFactureService service = new FicheFactureService(factures, champsLus, modifications,
			historique, comptes, dossiers, new HorlogeReglable(MAINTENANT));

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, MAINTENANT, MAINTENANT);
	private final Compte lucie = Compte.administrateurDe(cabinet, "lucie@cabinet-test.fr", "x", "Lucie Moreau");
	private final Dossier dupont = new Dossier(cabinet, "Boulangerie Dupont", "812345679", lucie, MAINTENANT);

	private Facture edf;
	private List<ChampLu> lus;

	@BeforeEach
	void base() {
		ReflectionTestUtils.setField(cabinet, "id", 7L);
		ReflectionTestUtils.setField(lucie, "id", 3L);
		ReflectionTestUtils.setField(dupont, "id", 42L);
		when(dossiers.findByIdAndCabinetId(42L, 7L)).thenReturn(Optional.of(dupont));
		when(comptes.findByDossierIdAndNumero(42L, "606"))
				.thenReturn(Optional.of(CompteComptable.general(42L, "606", "Achats non stockés", MAINTENANT)));
		CompteComptable desactive = CompteComptable.general(42L, "613", "Locations", MAINTENANT);
		desactive.setActif(false);
		when(comptes.findByDossierIdAndNumero(42L, "613")).thenReturn(Optional.of(desactive));

		// CA1 : facture EDF lue, TTC « À vérifier » à 186,00 €, contrepartie 606 (du plan du dossier).
		edf = facture(5L);
		edf.terminerLecture(new ResultatLecture(true, "FOURNISSEUR", "EDF", "F-2026-0611", LocalDate.of(2026, 6, 12),
				new BigDecimal("155.33"), new BigDecimal("31.07"), new BigDecimal("20.00"), new BigDecimal("186.00"),
				"606", Map.of(), Set.of(ChampFacture.MONTANT_TTC), List.of()), MAINTENANT);
		lus = List.of(new ChampLu(edf, ChampFacture.NOM_TIERS, 0.98, false),
				new ChampLu(edf, ChampFacture.MONTANT_TTC, 0.4, true),
				new ChampLu(edf, ChampFacture.COMPTE_CONTREPARTIE, 0.6, true));
		when(factures.findByIdAndDossierId(5L, 42L)).thenReturn(Optional.of(edf));
		when(champsLus.findByFactureId(5L)).thenReturn(lus);
	}

	private Facture facture(Long id) {
		Facture facture = new Facture(dupont, Facture.Sens.ACHAT, "edf.pdf", "application/pdf", MAINTENANT, lucie);
		ReflectionTestUtils.setField(facture, "id", id);
		return facture;
	}

	private static InformationsFacture corrigee(String numero, BigDecimal ttc, String compte) {
		return new InformationsFacture("FOURNISSEUR", "EDF", numero, LocalDate.of(2026, 6, 12),
				new BigDecimal("155.33"), new BigDecimal("31.07"), new BigDecimal("20.00"), ttc, compte);
	}

	private static List<ModificationFacture> enregistrees(ModificationFactureRepository modifications, int nombre) {
		ArgumentCaptor<ModificationFacture> capture = ArgumentCaptor.forClass(ModificationFacture.class);
		verify(modifications, times(nombre)).save(capture.capture());
		return capture.getAllValues();
	}

	// CA1 : TTC corrigé à 186,40 € puis « Valider » : « Traitée » (Reçue -> En cours -> Traitée, US-51 RG2),
	// correction gardée avec son auteur et sa date (RG8), plus rien « À vérifier », facture suivante (RG6).
	@Test
	void ca1CorrectionEtValidation() {
		Facture traitee = facture(4L);
		ReflectionTestUtils.setField(traitee, "statut", Facture.Statut.TRAITEE);
		Facture suivante = facture(6L);
		Facture aCompleter = facture(8L);
		ReflectionTestUtils.setField(aCompleter, "statut", Facture.Statut.A_COMPLETER);
		when(factures.findByDossierIdAndSensOrderByDeposeeLeDescIdDesc(42L, Facture.Sens.ACHAT))
				.thenReturn(List.of(aCompleter, edf, traitee, suivante));

		ResultatFiche resultat = service.valider(lucie, 42L, 5L, corrigee("F-2026-0611", new BigDecimal("186.40"), "606"));

		assertEquals(ResultatFiche.Issue.VALIDEE, resultat.issue());
		assertEquals(6L, resultat.suivante());
		assertEquals(Facture.Statut.TRAITEE, edf.getStatut());
		assertEquals(new BigDecimal("186.40"), edf.getMontantTtc());
		VueFacture.Fiche fiche = resultat.facture().fiche();
		assertEquals(Facture.Statut.TRAITEE, fiche.statutTraitement());
		assertFalse(fiche.modifiable());
		assertEquals(new BigDecimal("186.40"), fiche.informations().montantTtc());

		ModificationFacture modification = enregistrees(modifications, 1).get(0);
		assertEquals(ChampFacture.MONTANT_TTC, modification.getChamp());
		assertEquals("186", modification.getAncienneValeur());
		assertEquals("186.4", modification.getNouvelleValeur());
		assertEquals(lucie, modification.getModifiePar());
		assertEquals(MAINTENANT, modification.getModifieLe());
		assertEquals(edf, modification.getFacture());

		ArgumentCaptor<ChangementStatutFacture> statuts = ArgumentCaptor.forClass(ChangementStatutFacture.class);
		verify(historique, times(2)).save(statuts.capture());
		assertEquals(List.of(Facture.Statut.RECUE, Facture.Statut.EN_COURS),
				statuts.getAllValues().stream().map(ChangementStatutFacture::getAncienStatut).toList());
		assertEquals(List.of(Facture.Statut.EN_COURS, Facture.Statut.TRAITEE),
				statuts.getAllValues().stream().map(ChangementStatutFacture::getNouveauStatut).toList());
		assertTrue(statuts.getAllValues().stream().allMatch(s -> s.getModifiePar() == lucie));

		assertTrue(lus.stream().noneMatch(ChampLu::isAVerifier));
		assertFalse(edf.isLectureAVerifier());
		assertEquals(List.of(), resultat.facture().lecture().aVerifier());
	}

	// Facture déjà « En cours de traitement » : un seul changement ; pas de facture suivante.
	@Test
	void validationDepuisEnCours() {
		ReflectionTestUtils.setField(edf, "statut", Facture.Statut.EN_COURS);
		when(factures.findByDossierIdAndSensOrderByDeposeeLeDescIdDesc(42L, Facture.Sens.ACHAT))
				.thenReturn(List.of(edf));

		ResultatFiche resultat = service.valider(lucie, 42L, 5L, corrigee("F-2026-0611", new BigDecimal("186.40"), "606"));

		assertEquals(ResultatFiche.Issue.VALIDEE, resultat.issue());
		assertNull(resultat.suivante());
		verify(historique, times(1)).save(any());
	}

	// CA2, CA3, RG3 : refus avec le message ; rien n'est enregistré et le statut ne change pas.
	@Test
	void refusDeValidation() {
		Map<InformationsFacture, String> refus = Map.of(
				corrigee(null, new BigDecimal("186.40"), "606"), "Indiquez le numéro de facture",
				corrigee("  ", new BigDecimal("186.40"), "606"), "Indiquez le numéro de facture",
				new InformationsFacture(null, "EDF", "F1", LocalDate.of(2026, 6, 12), new BigDecimal("155.33"),
						new BigDecimal("31.07"), null, new BigDecimal("190.00"), "606"),
				"Montants incohérents : HT + TVA ≠ TTC",
				corrigee("F1", new BigDecimal("186.40"), "6061"),
				"Le compte 6061 n'est pas dans le plan comptable du dossier",
				corrigee("F1", new BigDecimal("186.40"), "613"),
				"Le compte 613 est désactivé dans le plan comptable du dossier",
				corrigee("F1", new BigDecimal("186.405"), "606"), "Montant TTC : 2 décimales au maximum");

		refus.forEach((demande, message) -> {
			ResultatFiche resultat = service.valider(lucie, 42L, 5L, demande);
			assertEquals(ResultatFiche.Issue.REFUSEE, resultat.issue());
			assertEquals(message, resultat.message());
		});
		assertEquals(Facture.Statut.RECUE, edf.getStatut());
		assertEquals(new BigDecimal("186.00"), edf.getMontantTtc());
		verify(modifications, never()).save(any());
		verify(historique, never()).save(any());
	}

	// CA4 / RG7 : facture en « Lecture échouée », tous les champs saisis à la main : traitée comme une lue.
	@Test
	void ca4SaisieManuelle() {
		Facture illisible = facture(9L);
		illisible.terminerLecture(ResultatLecture.illisible(), MAINTENANT);
		when(factures.findByIdAndDossierId(9L, 42L)).thenReturn(Optional.of(illisible));
		when(champsLus.findByFactureId(9L)).thenReturn(List.of());
		when(factures.findByDossierIdAndSensOrderByDeposeeLeDescIdDesc(42L, Facture.Sens.ACHAT))
				.thenReturn(List.of(illisible));

		ResultatFiche resultat = service.valider(lucie, 42L, 9L, corrigee("F-2026-0611", new BigDecimal("186.40"), "606"));

		assertEquals(ResultatFiche.Issue.VALIDEE, resultat.issue());
		assertEquals(Facture.Statut.TRAITEE, illisible.getStatut());
		assertEquals("EDF", illisible.getNomTiers());
		assertEquals(Facture.StatutLecture.ECHOUEE, illisible.getLectureStatut());
		assertFalse(illisible.isLectureAVerifier());
		assertEquals(9, enregistrees(modifications, 9).size());
	}

	// RG2 et RG8 : « Enregistrer » garde les corrections sans valider ; un champ corrigé n'est plus
	// « À vérifier », les autres doutes restent (pastille de la liste).
	@Test
	void rg2EnregistrementSansValidation() {
		ResultatFiche resultat = service.enregistrer(lucie, 42L, 5L,
				corrigee(null, new BigDecimal("186.40"), "606"));

		assertEquals(ResultatFiche.Issue.ENREGISTREE, resultat.issue());
		assertNull(resultat.suivante());
		assertEquals(Facture.Statut.RECUE, edf.getStatut());
		assertNull(edf.getNumeroFacture());
		List<ChampFacture> champs = enregistrees(modifications, 2).stream().map(ModificationFacture::getChamp)
				.toList();
		assertEquals(List.of(ChampFacture.NUMERO_FACTURE, ChampFacture.MONTANT_TTC), champs);
		assertFalse(lus.get(1).isAVerifier());
		assertTrue(lus.get(2).isAVerifier());
		assertTrue(edf.isLectureAVerifier());
		assertEquals(List.of(ChampFacture.COMPTE_CONTREPARTIE), resultat.facture().lecture().aVerifier());
		verify(historique, never()).save(any());
	}

	// Plus aucun doute ni champ vide après l'enregistrement : la pastille « À vérifier » disparaît.
	@Test
	void plusRienAVerifier() {
		lus = new ArrayList<>(List.of(new ChampLu(edf, ChampFacture.MONTANT_TTC, 0.4, true)));
		when(champsLus.findByFactureId(5L)).thenReturn(lus);

		service.enregistrer(lucie, 42L, 5L, corrigee("F-2026-0611", new BigDecimal("186.40"), "606"));

		assertFalse(edf.isLectureAVerifier());
	}

	// RG3 : la contrepartie lue (6061, hors du plan) peut rester en attendant ; une nouvelle doit être du plan.
	@Test
	void rg3ContrepartieALEnregistrement() {
		ReflectionTestUtils.setField(edf, "compteContrepartie", "6061");
		assertEquals(ResultatFiche.Issue.ENREGISTREE,
				service.enregistrer(lucie, 42L, 5L, corrigee(null, new BigDecimal("186.40"), "6061")).issue());

		ResultatFiche absent = service.enregistrer(lucie, 42L, 5L, corrigee(null, new BigDecimal("186.40"), "6063"));
		assertEquals("Le compte 6063 n'est pas dans le plan comptable du dossier", absent.message());
		ResultatFiche invalide = service.enregistrer(lucie, 42L, 5L,
				new InformationsFacture("AUTRE", null, null, null, null, null, null, null, null));
		assertEquals("Type de tiers inconnu : choisissez Fournisseur ou Client", invalide.message());
	}

	// RG8 : facture traitée en lecture seule ; facture en cours de lecture : on attend son résultat.
	@Test
	void rg8NonModifiable() {
		ReflectionTestUtils.setField(edf, "statut", Facture.Statut.TRAITEE);
		InformationsFacture demande = corrigee("F1", new BigDecimal("186.40"), "606");
		assertEquals(ResultatFiche.Issue.NON_MODIFIABLE, service.valider(lucie, 42L, 5L, demande).issue());
		assertEquals(ResultatFiche.Issue.NON_MODIFIABLE, service.enregistrer(lucie, 42L, 5L, demande).issue());

		ReflectionTestUtils.setField(edf, "statut", Facture.Statut.RECUE);
		ReflectionTestUtils.setField(edf, "lectureStatut", Facture.StatutLecture.EN_COURS);
		assertEquals(ResultatFiche.Issue.NON_MODIFIABLE, service.enregistrer(lucie, 42L, 5L, demande).issue());
		verify(modifications, never()).save(any());
	}

	// Droits du dossier (US-11 RG3) : autre dossier ou facture d'un autre dossier.
	@Test
	void introuvable() {
		InformationsFacture demande = corrigee("F1", new BigDecimal("186.40"), "606");
		assertEquals(ResultatFiche.Issue.INTROUVABLE, service.valider(lucie, 99L, 5L, demande).issue());
		assertEquals(ResultatFiche.Issue.INTROUVABLE, service.enregistrer(lucie, 42L, 77L, demande).issue());
	}

	// Logs : l'issue, jamais les valeurs des champs (tiers, montants, numéros, comptes).
	@Test
	void logsSansValeurs() {
		when(factures.findByDossierIdAndSensOrderByDeposeeLeDescIdDesc(42L, Facture.Sens.ACHAT))
				.thenReturn(List.of(edf));
		try (JournalCapture journal = JournalCapture.de(EvenementMetier.class)) {
			service.valider(lucie, 42L, 5L, corrigee("F-2026-0611", new BigDecimal("190.00"), "606"));
			service.valider(lucie, 42L, 5L, corrigee("F-2026-0611", new BigDecimal("186.40"), "606"));

			List<ILoggingEvent> lignes = journal.lignes();
			assertEquals(List.of(Level.WARN, Level.INFO), lignes.stream().map(ILoggingEvent::getLevel).toList());
			for (ILoggingEvent ligne : lignes) {
				String texte = ligne.getFormattedMessage() + JournalCapture.champs(ligne);
				assertTrue(texte.contains("facture.validation"));
				assertFalse(texte.contains("EDF") || texte.contains("186") || texte.contains("F-2026")
						|| texte.contains("606"));
			}
		}
	}

}
