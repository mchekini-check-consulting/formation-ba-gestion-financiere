package com.example.gestionfinanciere.banque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import com.example.gestionfinanciere.ecriture.EcritureRepository;
import com.example.gestionfinanciere.ecriture.EcritureService;
import com.example.gestionfinanciere.plancomptable.CompteComptableRepository;

class TransactionServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-18T09:00:00Z");
	private static final LocalDate JOUR = LocalDate.of(2026, 11, 18);

	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final OperationValideeRepository operations = mock(OperationValideeRepository.class);
	private final EcritureService ecritures = new EcritureService(mock(EcritureRepository.class),
			mock(CompteComptableRepository.class));

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, MAINTENANT, MAINTENANT);
	private final Compte jean = Compte.administrateurDe(cabinet, "jean.dupont@cabinet-test.fr", "x", "Jean Dupont");
	private final Dossier boulangerie = new Dossier(cabinet, "Boulangerie Dupont", "812345679", jean, MAINTENANT);

	@BeforeEach
	void base() {
		ReflectionTestUtils.setField(cabinet, "id", 7L);
		ReflectionTestUtils.setField(jean, "id", 3L);
		when(dossiers.findByIdAndCabinetId(42L, 7L)).thenReturn(Optional.of(boulangerie));
	}

	@SuppressWarnings("unchecked")
	private TransactionService service(FournisseurTransactions... fournisseurs) {
		ObjectProvider<FournisseurTransactions> fournisseur = mock(ObjectProvider.class);
		when(fournisseur.orderedStream()).thenReturn(Stream.of(fournisseurs));
		return new TransactionService(dossiers, fournisseur, operations, ecritures);
	}

	private static Transaction transaction(LocalDate date, String libelle, Transaction.Source source) {
		return new Transaction(libelle + "-" + source, date, libelle, "EDF", new BigDecimal("10.00"),
				Transaction.Sens.SORTIE, source);
	}

	// Bouchon désactivé et aucune connexion bancaire : « Aucune transaction ».
	@Test
	void aucunFournisseur() {
		assertEquals(Optional.of(List.of()), service().lister(jean, 42L));
	}

	// Transactions de tous les fournisseurs activés, les plus récentes d'abord.
	@Test
	void plusRecentesDAbord() {
		Transaction ancienne = transaction(JOUR.minusDays(5), "A", Transaction.Source.BOUCHON);
		Transaction recenteB = transaction(JOUR, "B", Transaction.Source.BOUCHON);
		Transaction recenteA = transaction(JOUR, "A", Transaction.Source.BANQUE);
		FournisseurTransactions bouchon = dossier -> List.of(ancienne, recenteB);
		FournisseurTransactions banque = dossier -> List.of(recenteA);

		List<VueTransaction> liste = service(bouchon, banque).lister(jean, 42L).orElseThrow();
		assertEquals(List.of("A-BANQUE", "B-BOUCHON", "A-BOUCHON"), liste.stream().map(VueTransaction::reference).toList());
		assertNull(liste.get(0).validation());
	}

	// US-30 : une transaction validée porte sa catégorie, son compte, son taux et qui l'a validée.
	@Test
	void us30TransactionValidee() {
		Transaction edf = transaction(JOUR, "EDF", Transaction.Source.BOUCHON);
		OperationValidee validee = new OperationValidee(42L, edf, "ACHAT", "607", new BigDecimal("20"), jean, MAINTENANT);
		ReflectionTestUtils.setField(validee, "id", 9L);
		when(operations.findByDossierId(42L)).thenReturn(List.of(validee));

		VueTransaction.Validation validation = service(dossier -> List.of(edf)).lister(jean, 42L).orElseThrow().get(0)
				.validation();
		assertEquals(new VueTransaction.Validation("ACHAT", "607", new BigDecimal("20"), "Jean Dupont", MAINTENANT,
				null), validation);
	}

	// Droits du dossier (US-11 RG3) : dossier d'un autre cabinet -> rien.
	@Test
	void dossierDUnAutreCabinet() {
		FournisseurTransactions bouchon = dossier -> List.of(transaction(JOUR, "A", Transaction.Source.BOUCHON));
		assertTrue(service(bouchon).lister(jean, 99L).isEmpty());
	}

}
