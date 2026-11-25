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
import java.time.Instant;
import java.time.LocalDate;
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
import com.example.gestionfinanciere.facture.lecture.AnalyseurFacture;
import com.example.gestionfinanciere.facture.lecture.ResultatLecture;
import com.example.gestionfinanciere.facture.lecture.TvaParTaux;
import com.example.gestionfinanciere.plancomptable.CompteComptable;
import com.example.gestionfinanciere.plancomptable.CompteComptableRepository;

// US-55 Vérifier, corriger et valider les informations d'une facture.
class CorrectionFactureServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-25T09:00:00Z");
	private static final LocalDate EMISE_LE = LocalDate.of(2026, 6, 12);

	private final FactureRepository factures = mock(FactureRepository.class);
	private final ChampLuRepository champsLus = mock(ChampLuRepository.class);
	private final ChangementStatutFactureRepository historique = mock(ChangementStatutFactureRepository.class);
	private final CorrectionFactureRepository corrections = mock(CorrectionFactureRepository.class);
	private final CompteComptableRepository comptes = mock(CompteComptableRepository.class);
	private final DossierRepository dossiers = mock(DossierRepository.class);

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, MAINTENANT, MAINTENANT);
	private final Compte lucie = Compte.administrateurDe(cabinet, "lucie@cabinet-test.fr", "x", "Lucie Bernard");
	private final Dossier dossier = new Dossier(cabinet, "Boulangerie Martin", "812345679", lucie, MAINTENANT);
	private final CorrectionFactureService service = new CorrectionFactureService(factures, champsLus, historique,
			corrections, comptes, dossiers, new VuesFacture(champsLus, comptes, corrections),
			new HorlogeReglable(MAINTENANT));

	private Facture facture;
	private ChampLu ttcDouteux;
	private ChampLu nomSur;

	@BeforeEach
	void base() {
		ReflectionTestUtils.setField(cabinet, "id", 7L);
		ReflectionTestUtils.setField(lucie, "id", 3L);
		ReflectionTestUtils.setField(dossier, "id", 42L);
		when(dossiers.findByIdAndCabinetId(42L, 7L)).thenReturn(Optional.of(dossier));
		facture = new Facture(dossier, Facture.Sens.ACHAT, "edf.pdf", "application/pdf", MAINTENANT, lucie,
				new byte[] { 1 });
		ReflectionTestUtils.setField(facture, "id", 5L);
		facture.demarrerLecture();
		// CA1 : la facture EDF lue avec un TTC « À vérifier » à 186,00 €.
		facture.terminerLecture(new ResultatLecture(true, "FOURNISSEUR", "EDF", "F-2026-0611", EMISE_LE,
				new BigDecimal("155.33"), new BigDecimal("31.07"), new BigDecimal("20"), new BigDecimal("186.00"),
				"606", Map.of(ChampFacture.MONTANT_TTC, 0.4), Set.of(ChampFacture.MONTANT_TTC), List.of()),
				MAINTENANT);
		when(factures.findByIdAndDossierId(5L, 42L)).thenReturn(Optional.of(facture));
		ttcDouteux = new ChampLu(facture, ChampFacture.MONTANT_TTC, 0.4, true);
		nomSur = new ChampLu(facture, ChampFacture.NOM_TIERS, 0.9, false);
		when(champsLus.findByFactureId(5L)).thenReturn(List.of(ttcDouteux, nomSur));
		when(comptes.findByDossierIdAndNumero(42L, "606")).thenReturn(Optional
				.of(CompteComptable.general(42L, "606", "Achats non stockés de matières et fournitures", MAINTENANT)));
	}

	private static InformationsFacture edf(String ttc) {
		return new InformationsFacture("FOURNISSEUR", "EDF", "F-2026-0611", EMISE_LE, new BigDecimal("155.33"),
				new BigDecimal("31.07"), new BigDecimal("20"), ttc == null ? null : new BigDecimal(ttc), "606");
	}

	// CA1 : le TTC corrigé à 186,40 € puis « Valider » : « Traitée », historique et doutes levés.
	@Test
	void ca1CorrectionEtValidation() {
		ResultatCorrection resultat = service.valider(lucie, 42L, 5L, edf("186.40"));

		assertEquals(ResultatCorrection.Issue.VALIDEE, resultat.issue());
		assertEquals(Facture.Statut.TRAITEE, facture.getStatut());
		assertEquals(0, new BigDecimal("186.40").compareTo(facture.getMontantTtc()));
		assertFalse(facture.isLectureAVerifier());
		assertFalse(ttcDouteux.isAVerifier());
		ArgumentCaptor<ChangementStatutFacture> changement = ArgumentCaptor.forClass(ChangementStatutFacture.class);
		verify(historique).save(changement.capture());
		assertEquals(Facture.Statut.RECUE, changement.getValue().getAncienStatut());
		assertEquals(Facture.Statut.TRAITEE, changement.getValue().getNouveauStatut());
		ArgumentCaptor<CorrectionFacture> correction = ArgumentCaptor.forClass(CorrectionFacture.class);
		verify(corrections).save(correction.capture());
		assertEquals("MONTANT_TTC", correction.getValue().getChamps());
		assertEquals(lucie, correction.getValue().getModifiePar());
		assertEquals(MAINTENANT, correction.getValue().getModifieLe());
		assertEquals(Facture.Statut.TRAITEE, resultat.facture().statut());
		assertEquals("Achats non stockés de matières et fournitures", resultat.facture().lecture().libelleContrepartie());
	}

	// RG2 et RG8 : seul le champ modifié n'est plus « À vérifier » ; le statut ne change pas.
	@Test
	void rg2EnregistrerSansValider() {
		InformationsFacture corrigee = new InformationsFacture("FOURNISSEUR", "  EDF Entreprises  ", "F-2026-0611",
				EMISE_LE, new BigDecimal("155.33"), new BigDecimal("31.07"), new BigDecimal("20.00"),
				new BigDecimal("186.0"), "606");

		ResultatCorrection resultat = service.enregistrer(lucie, 42L, 5L, corrigee);

		assertEquals(ResultatCorrection.Issue.ENREGISTREE, resultat.issue());
		assertEquals("EDF Entreprises", facture.getNomTiers());
		assertEquals(Facture.Statut.RECUE, facture.getStatut());
		// Le TTC n'a pas changé (186,0 = 186,00) : toujours « À vérifier ».
		assertTrue(ttcDouteux.isAVerifier());
		assertTrue(facture.isLectureAVerifier());
		ArgumentCaptor<CorrectionFacture> correction = ArgumentCaptor.forClass(CorrectionFacture.class);
		verify(corrections).save(correction.capture());
		assertEquals("NOM_TIERS", correction.getValue().getChamps());
		verify(historique, never()).save(any());
		assertEquals(List.of(ChampFacture.MONTANT_TTC), resultat.facture().lecture().aVerifier());
	}

	// Rien de changé : pas de trace de modification.
	@Test
	void rienDeChange() {
		assertEquals(ResultatCorrection.Issue.ENREGISTREE, service.enregistrer(lucie, 42L, 5L, edf("186.00")).issue());
		verify(corrections, never()).save(any());
	}

	// RG2 : un champ vidé redevient « À renseigner » ; la pastille de la liste reste « À vérifier ».
	@Test
	void champVide() {
		InformationsFacture sansNumero = new InformationsFacture("FOURNISSEUR", "EDF", " ", EMISE_LE,
				new BigDecimal("155.33"), new BigDecimal("31.07"), new BigDecimal("20"), new BigDecimal("186.40"),
				"606");
		ResultatCorrection resultat = service.enregistrer(lucie, 42L, 5L, sansNumero);
		assertNull(facture.getNumeroFacture());
		assertFalse(ttcDouteux.isAVerifier());
		assertTrue(facture.isLectureAVerifier());
		assertEquals(List.of(ChampFacture.NUMERO_FACTURE), resultat.facture().lecture().aRenseigner());
	}

	// CA2 et RG5 : champ obligatoire manquant, message qui nomme le champ, rien n'est enregistré.
	@Test
	void ca2ChampObligatoireManquant() {
		InformationsFacture sansNumero = new InformationsFacture("FOURNISSEUR", "EDF", null, EMISE_LE,
				new BigDecimal("155.33"), new BigDecimal("31.07"), null, new BigDecimal("186.40"), "606");
		ResultatCorrection resultat = service.valider(lucie, 42L, 5L, sansNumero);
		assertEquals(ResultatCorrection.Issue.INVALIDE, resultat.issue());
		assertEquals("Indiquez le numéro de facture", resultat.message());
		assertEquals(Facture.Statut.RECUE, facture.getStatut());
		assertEquals("F-2026-0611", facture.getNumeroFacture());
		verify(corrections, never()).save(any());

		assertEquals("Indiquez le tiers", service.valider(lucie, 42L, 5L, null).message());
		assertEquals("Indiquez le montant TTC", service.valider(lucie, 42L, 5L, edf(null)).message());
	}

	// CA3 et RG4 : HT + TVA ≠ TTC.
	@Test
	void ca3MontantsIncoherents() {
		ResultatCorrection resultat = service.valider(lucie, 42L, 5L, edf("190.00"));
		assertEquals(ResultatCorrection.Issue.INVALIDE, resultat.issue());
		assertEquals("Montants incohérents : HT + TVA ≠ TTC", resultat.message());
		assertEquals(Facture.Statut.RECUE, facture.getStatut());
	}

	// CA4 et RG7 : facture en « Lecture échouée », saisie à la main puis validée.
	@Test
	void ca4SaisieManuelle() {
		facture.terminerLecture(ResultatLecture.illisible(), MAINTENANT);
		when(champsLus.findByFactureId(5L)).thenReturn(List.of());
		ResultatCorrection resultat = service.valider(lucie, 42L, 5L, edf("186.40"));
		assertEquals(ResultatCorrection.Issue.VALIDEE, resultat.issue());
		assertEquals(Facture.StatutLecture.ECHOUEE, resultat.facture().lecture().statut());
		assertEquals("EDF", resultat.facture().lecture().nomTiers());
		ArgumentCaptor<CorrectionFacture> correction = ArgumentCaptor.forClass(CorrectionFacture.class);
		verify(corrections).save(correction.capture());
		assertEquals("TYPE_TIERS,NOM_TIERS,NUMERO_FACTURE,DATE_EMISSION,MONTANT_HT,MONTANT_TVA,TAUX_TVA,MONTANT_TTC,"
				+ "COMPTE_CONTREPARTIE", correction.getValue().getChamps());
	}

	// RG6 : « Valider » depuis « À compléter » (et tout statut sauf « Traitée »).
	@Test
	void rg6ValiderDepuisACompleter() {
		facture.changerStatut(Facture.Statut.A_COMPLETER, "Numéro manquant");
		service.valider(lucie, 42L, 5L, edf("186.40"));
		assertEquals(Facture.Statut.TRAITEE, facture.getStatut());
		assertNull(facture.getMotifACompleter());
	}

	// RG3 : la contrepartie est un compte actif du plan comptable du dossier.
	@Test
	void rg3CompteDuPlan() {
		InformationsFacture compte607 = new InformationsFacture("FOURNISSEUR", "EDF", "F-2026-0611", EMISE_LE,
				new BigDecimal("155.33"), new BigDecimal("31.07"), new BigDecimal("20"), new BigDecimal("186.40"), "607");
		assertEquals("Le compte 607 n'est pas dans le plan comptable du dossier",
				service.enregistrer(lucie, 42L, 5L, compte607).message());

		CompteComptable desactive = CompteComptable.general(42L, "607", "Achats de marchandises", MAINTENANT);
		desactive.setActif(false);
		when(comptes.findByDossierIdAndNumero(42L, "607")).thenReturn(Optional.of(desactive));
		assertEquals("Le compte 607 est désactivé dans le plan comptable du dossier",
				service.enregistrer(lucie, 42L, 5L, compte607).message());
		assertEquals("606", facture.getCompteContrepartie());
	}

	// Formats refusés, dans l'ordre de la fiche.
	@Test
	void formatsRefuses() {
		assertEquals("Type de tiers : Fournisseur ou Client", message(new InformationsFacture("BANQUE", null, null, null,
				null, null, null, null, null)));
		assertEquals("Nom du tiers : 255 caractères au plus", message(new InformationsFacture(null, "x".repeat(256),
				null, null, null, null, null, null, null)));
		assertEquals("Numéro de facture : 100 caractères au plus", message(new InformationsFacture(null, null,
				"1".repeat(101), null, null, null, null, null, null)));
		assertEquals("Montant HT : nombre à 2 décimales au plus", message(new InformationsFacture(null, null, null,
				null, new BigDecimal("1.005"), null, null, null, null)));
		assertEquals("Montant TVA : nombre à 2 décimales au plus", message(new InformationsFacture(null, null, null,
				null, null, new BigDecimal("10000000000"), null, null, null)));
		assertEquals("Taux de TVA : entre 0 et 100 %", message(new InformationsFacture(null, null, null, null, null,
				null, new BigDecimal("120"), null, null)));
		assertEquals("Taux de TVA : entre 0 et 100 %", message(new InformationsFacture(null, null, null, null, null,
				null, new BigDecimal("-5"), null, null)));
		assertEquals("Taux de TVA : entre 0 et 100 %", message(new InformationsFacture(null, null, null, null, null,
				null, new BigDecimal("5.555"), null, null)));
		assertEquals("Montant TTC : nombre à 2 décimales au plus", message(new InformationsFacture(null, null, null,
				null, null, null, null, new BigDecimal("0.001"), null)));
		// Un avoir a des montants négatifs : accepté.
		assertNull(message(new InformationsFacture("CLIENT", null, null, null, new BigDecimal("-100"), null, null,
				null, null)));
	}

	private String message(InformationsFacture informations) {
		return service.enregistrer(lucie, 42L, 5L, informations).message();
	}

	// Une facture traitée ou en cours de lecture ne se modifie pas.
	@Test
	void factureFigee() {
		facture.valider();
		ResultatCorrection traitee = service.enregistrer(lucie, 42L, 5L, edf("186.40"));
		assertEquals(ResultatCorrection.Issue.FIGEE, traitee.issue());
		assertEquals("Facture traitée : ses informations ne se modifient plus", traitee.message());

		Facture enLecture = new Facture(dossier, Facture.Sens.ACHAT, "ticket.png", "image/png", MAINTENANT, lucie,
				new byte[] { 1 });
		enLecture.demarrerLecture();
		when(factures.findByIdAndDossierId(6L, 42L)).thenReturn(Optional.of(enLecture));
		assertEquals("Lecture en cours : réessayez dans quelques secondes",
				service.valider(lucie, 42L, 6L, edf("186.40")).message());
	}

	// Droits du dossier (US-11 RG3) : autre cabinet ou facture d'un autre dossier.
	@Test
	void introuvable() {
		assertEquals(ResultatCorrection.Issue.INTROUVABLE, service.enregistrer(lucie, 99L, 5L, edf("1")).issue());
		assertEquals(ResultatCorrection.Issue.INTROUVABLE, service.valider(lucie, 42L, 77L, edf("1")).issue());
	}

	// US-54 CA2 : taux vide d'une facture à plusieurs taux, toujours « Multiple » ; un taux saisi le remplace.
	@Test
	void tauxMultiple() {
		facture.terminerLecture(new ResultatLecture(true, "CLIENT", "Café Lumière", "0042", EMISE_LE,
				new BigDecimal("150.00"), new BigDecimal("21.00"), null, new BigDecimal("171.00"), "706",
				Map.of(), Set.of(), List.of(new TvaParTaux(new BigDecimal("20"), new BigDecimal("100.00"),
						new BigDecimal("20.00")), new TvaParTaux(new BigDecimal("2"), new BigDecimal("50.00"),
								new BigDecimal("1.00")))),
				MAINTENANT);
		when(comptes.findByDossierIdAndNumero(42L, "706")).thenReturn(Optional.empty());
		InformationsFacture memes = new InformationsFacture("CLIENT", "Café Lumière", "0042", EMISE_LE,
				new BigDecimal("150"), new BigDecimal("21"), null, new BigDecimal("171"), null);

		ResultatCorrection resultat = service.enregistrer(lucie, 42L, 5L, memes);
		assertTrue(facture.isTauxTvaMultiple());
		assertEquals(2, resultat.facture().lecture().detailTva().size());
		ArgumentCaptor<CorrectionFacture> correction = ArgumentCaptor.forClass(CorrectionFacture.class);
		verify(corrections).save(correction.capture());
		assertEquals("COMPTE_CONTREPARTIE", correction.getValue().getChamps());

		InformationsFacture unTaux = new InformationsFacture("CLIENT", "Café Lumière", "0042", EMISE_LE,
				new BigDecimal("150"), new BigDecimal("21"), new BigDecimal("14"), new BigDecimal("171"), null);
		service.enregistrer(lucie, 42L, 5L, unTaux);
		assertFalse(facture.isTauxTvaMultiple());
		assertTrue(facture.getDetailTva().isEmpty());
	}

	// Libellé de la contrepartie : plan comptable du dossier, à défaut le plan général ; dernière modification.
	@Test
	void vueDeLaFiche() {
		CorrectionFacture derniere = new CorrectionFacture(facture, "NOM_TIERS", lucie, MAINTENANT);
		when(corrections.findFirstByFactureIdOrderByModifieLeDescIdDesc(5L)).thenReturn(Optional.of(derniere));
		VueFacture vue = new VuesFacture(champsLus, comptes, corrections).vue(facture);
		assertEquals(new VueFacture.Modification("Lucie Bernard", MAINTENANT), vue.modification());
		assertEquals(Facture.Statut.RECUE, vue.statut());

		when(comptes.findByDossierIdAndNumero(42L, "606")).thenReturn(Optional.empty());
		assertEquals(AnalyseurFacture.libelleCompte("606"),
				new VuesFacture(champsLus, comptes, corrections).vue(facture).lecture().libelleContrepartie());
	}

}
