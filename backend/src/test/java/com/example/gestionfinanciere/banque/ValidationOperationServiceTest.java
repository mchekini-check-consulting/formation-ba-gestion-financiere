package com.example.gestionfinanciere.banque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.dossier.Dossier;
import com.example.gestionfinanciere.dossier.DossierRepository;
import com.example.gestionfinanciere.ecriture.Ecriture;
import com.example.gestionfinanciere.ecriture.EcritureRepository;
import com.example.gestionfinanciere.ecriture.EcritureService;
import com.example.gestionfinanciere.ecriture.VueEcriture;
import com.example.gestionfinanciere.plancomptable.Categorie;
import com.example.gestionfinanciere.plancomptable.CategorieRepository;
import com.example.gestionfinanciere.plancomptable.CompteComptable;
import com.example.gestionfinanciere.plancomptable.CompteComptableRepository;

// US-30 Générer les écritures comptables des opérations bancaires validées.
class ValidationOperationServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-27T09:00:00Z");
	private static final LocalDate JOUR = LocalDate.of(2026, 11, 25);

	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final OperationValideeRepository operations = mock(OperationValideeRepository.class);
	private final CategorieRepository categories = mock(CategorieRepository.class);
	private final EcritureRepository ecritures = mock(EcritureRepository.class);
	private final CompteComptableRepository comptes = mock(CompteComptableRepository.class);
	private final EcritureService ecritureService = new EcritureService(ecritures, comptes);

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, MAINTENANT, MAINTENANT);
	private final Compte lucie = Compte.administrateurDe(cabinet, "lucie@cabinet-test.fr", "x", "Lucie Bernard");
	private final Dossier dossier = new Dossier(cabinet, "Boulangerie Martin", "812345679", lucie, MAINTENANT);

	private final Transaction edf = new Transaction("bouchon-22", JOUR, "PRLV SEPA EDF - Facture electricite octobre",
			"EDF", new BigDecimal("186.40"), Transaction.Sens.SORTIE, Transaction.Source.BOUCHON);
	private final Transaction fournil = new Transaction("bouchon-24", JOUR, "VIR Restaurant Le Fournil", "Le Fournil",
			new BigDecimal("640.00"), Transaction.Sens.ENTREE, Transaction.Source.BOUCHON);
	private final Transaction frais = new Transaction("bouchon-30", JOUR, "FRAIS TENUE DE COMPTE", "Banque",
			new BigDecimal("12.00"), Transaction.Sens.SORTIE, Transaction.Source.BOUCHON);

	private ValidationOperationService service;
	private Ecriture enregistree;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void base() {
		ReflectionTestUtils.setField(cabinet, "id", 7L);
		ReflectionTestUtils.setField(lucie, "id", 3L);
		ReflectionTestUtils.setField(dossier, "id", 42L);
		when(dossiers.findByIdAndCabinetId(42L, 7L)).thenReturn(Optional.of(dossier));
		ObjectProvider<FournisseurTransactions> fournisseurs = mock(ObjectProvider.class);
		when(fournisseurs.orderedStream()).thenAnswer(appel -> Stream.of((FournisseurTransactions) d -> List.of(edf,
				fournil, frais)));
		TransactionService transactions = new TransactionService(dossiers, fournisseurs, operations, ecritureService);
		service = new ValidationOperationService(dossiers, transactions, operations, categories, ecritureService,
				Clock.fixed(MAINTENANT, ZoneOffset.UTC));

		when(categories.findByCode("ACHAT")).thenReturn(Optional.of(new Categorie("ACHAT", "Achat", "607", 1)));
		when(categories.findByCode("VENTE")).thenReturn(Optional.of(new Categorie("VENTE", "Vente", "707", 2)));
		when(categories.findByCode("FRAIS_BANCAIRES"))
				.thenReturn(Optional.of(new Categorie("FRAIS_BANCAIRES", "Frais bancaires", "627", 3)));
		List<CompteComptable> plan = Stream.of("512", "607", "627", "707", "44566", "44571")
				.map(n -> CompteComptable.general(42L, n, "Compte " + n, MAINTENANT)).toList();
		when(comptes.findByDossierIdAndNumero(eq(42L), anyString())).thenAnswer(
				appel -> plan.stream().filter(c -> c.getNumero().equals(appel.getArgument(1))).findFirst());
		when(comptes.findByDossierIdOrderByNumero(42L)).thenReturn(plan);
		when(operations.save(any(OperationValidee.class))).thenAnswer(appel -> {
			OperationValidee operation = appel.getArgument(0);
			ReflectionTestUtils.setField(operation, "id", 9L);
			return operation;
		});
		enregistree = null;
		when(ecritures.save(any(Ecriture.class))).thenAnswer(appel -> {
			enregistree = appel.getArgument(0);
			return enregistree;
		});
		when(ecritures.findByOperationId(9L)).thenAnswer(appel -> Optional.ofNullable(enregistree));
	}

	private static BigDecimal euros(String montant) {
		return new BigDecimal(montant);
	}

	private static List<String> lignes(VueEcriture ecriture) {
		return ecriture.lignes().stream()
				.map(l -> l.numeroCompte() + " D " + l.debit() + " C " + l.credit()).toList();
	}

	// CA1 : prélèvement EDF de 186,40 € validé en « Achat » : 607 et 44566 au débit, 512 au crédit.
	@Test
	void ca1PrelevementEdfEnAchat() {
		ResultatValidation resultat = service.valider(lucie, 42L, "bouchon-22",
				new DemandeValidation("ACHAT", null, euros("20")));

		assertEquals(ResultatValidation.Issue.VALIDEE, resultat.issue());
		VueTransaction.Validation validation = resultat.transaction().validation();
		assertEquals("ACHAT", validation.categorie());
		assertEquals("607", validation.numeroCompte());
		assertEquals("Lucie Bernard", validation.valideePar());
		VueEcriture ecriture = validation.ecriture();
		assertEquals(Ecriture.Journal.BQ, ecriture.journal());
		assertEquals(JOUR, ecriture.date());
		assertEquals("PRLV SEPA EDF - Facture electricite octobre", ecriture.libelle());
		assertEquals(List.of("607 D 155.33 C 0.00", "44566 D 31.07 C 0.00", "512 D 0.00 C 186.40"), lignes(ecriture));
		// RG2 : toujours équilibrée.
		assertEquals(euros("186.40"), ecriture.totalDebit());
		assertEquals(euros("186.40"), ecriture.totalCredit());
		assertEquals(Ecriture.Journal.BQ, enregistree.getJournal());
		assertEquals(9L, enregistree.getOperationId());
		assertNull(enregistree.getFactureId());
	}

	// RG1 : encaissement en « Vente » : 512 au débit, compte de vente et TVA collectée au crédit.
	@Test
	void rg1EncaissementEnVente() {
		VueEcriture ecriture = service.valider(lucie, 42L, "bouchon-24", new DemandeValidation("VENTE", "", euros("20")))
				.transaction().validation().ecriture();
		assertEquals(List.of("512 D 640.00 C 0.00", "707 D 0.00 C 533.33", "44571 D 0.00 C 106.67"), lignes(ecriture));
	}

	// RG1 : « compte de TVA si besoin » : taux 0, pas de ligne de TVA ; autre compte choisi.
	@Test
	void rg1SansTva() {
		VueEcriture ecriture = service
				.valider(lucie, 42L, "bouchon-30", new DemandeValidation("FRAIS_BANCAIRES", " 627 ", euros("0")))
				.transaction().validation().ecriture();
		assertEquals(List.of("627 D 12.00 C 0.00", "512 D 0.00 C 12.00"), lignes(ecriture));
	}

	// Catégorie, taux ou compte refusés : message, rien n'est enregistré.
	@Test
	void refus() {
		assertEquals(ValidationOperationService.MESSAGE_CATEGORIE, message(null));
		assertEquals(ValidationOperationService.MESSAGE_CATEGORIE, message(new DemandeValidation("LOYER", null, euros("20"))));
		assertEquals(ValidationOperationService.MESSAGE_TAUX, message(new DemandeValidation("ACHAT", null, null)));
		assertEquals(ValidationOperationService.MESSAGE_TAUX, message(new DemandeValidation("ACHAT", null, euros("120"))));
		assertEquals(ValidationOperationService.MESSAGE_TAUX, message(new DemandeValidation("ACHAT", null, euros("-1"))));
		assertEquals(ValidationOperationService.MESSAGE_TAUX, message(new DemandeValidation("ACHAT", null, euros("5.555"))));
		assertEquals("Le compte 6061 n'est pas dans le plan comptable du dossier",
				message(new DemandeValidation("ACHAT", "6061", euros("20"))));
		verify(operations, never()).save(any());
		verify(ecritures, never()).save(any());
	}

	private String message(DemandeValidation demande) {
		ResultatValidation resultat = service.valider(lucie, 42L, "bouchon-22", demande);
		assertEquals(ResultatValidation.Issue.INVALIDE, resultat.issue());
		return resultat.message();
	}

	// Une opération ne se valide qu'une fois ; opération ou dossier introuvable.
	@Test
	void dejaValideeOuIntrouvable() {
		DemandeValidation achat = new DemandeValidation("ACHAT", null, euros("20"));
		when(operations.findByDossierIdAndReference(42L, "bouchon-22")).thenReturn(Optional.of(mock(OperationValidee.class)));
		ResultatValidation deja = service.valider(lucie, 42L, "bouchon-22", achat);
		assertEquals(ResultatValidation.Issue.DEJA_VALIDEE, deja.issue());
		assertEquals("Opération déjà validée", deja.message());

		assertEquals(ResultatValidation.Issue.INTROUVABLE, service.valider(lucie, 42L, "inconnue", achat).issue());
		assertEquals(ResultatValidation.Issue.INTROUVABLE, service.valider(lucie, 99L, "bouchon-24", achat).issue());
		assertTrue(enregistree == null);
	}

}
