package com.example.gestionfinanciere.banque;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class TransactionServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-18T09:00:00Z");
	private static final LocalDate JOUR = LocalDate.of(2026, 11, 18);

	private final DossierRepository dossiers = mock(DossierRepository.class);

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
		return new TransactionService(dossiers, fournisseur);
	}

	private static Transaction transaction(LocalDate date, String libelle, Transaction.Source source) {
		return new Transaction(date, libelle, "EDF", new BigDecimal("10.00"), Transaction.Sens.SORTIE, source);
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

		assertEquals(List.of(recenteA, recenteB, ancienne), service(bouchon, banque).lister(jean, 42L).orElseThrow());
	}

	// Droits du dossier (US-11 RG3) : dossier d'un autre cabinet -> rien.
	@Test
	void dossierDUnAutreCabinet() {
		FournisseurTransactions bouchon = dossier -> List.of(transaction(JOUR, "A", Transaction.Source.BOUCHON));
		assertTrue(service(bouchon).lister(jean, 99L).isEmpty());
	}

}
