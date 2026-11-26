package com.example.gestionfinanciere.plancomptable;

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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.HorlogeReglable;
import com.example.gestionfinanciere.dossier.Dossier;
import com.example.gestionfinanciere.dossier.DossierRepository;
import com.example.gestionfinanciere.plancomptable.ResultatCompte.Statut;

class PlanComptableServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-19T09:00:00Z");

	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final CompteComptableRepository comptes = mock(CompteComptableRepository.class);
	private final CategorieRepository categories = mock(CategorieRepository.class);
	private final PlanComptableService service = new PlanComptableService(dossiers, comptes, categories,
			new HorlogeReglable(MAINTENANT));

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, MAINTENANT, MAINTENANT);
	private final Compte lucie = Compte.administrateurDe(cabinet, "lucie@cabinet-test.fr", "x", "Lucie Martin");
	private final Dossier leroy = new Dossier(cabinet, "Boulangerie Leroy SARL", "812345679", lucie, MAINTENANT);

	private final CompteComptable fournisseurs = CompteComptable.general(42L, "401", "Fournisseurs", MAINTENANT);
	private final CompteComptable banque = CompteComptable.general(42L, "512", "Banque", MAINTENANT);
	private final CompteComptable moulins = CompteComptable.sousCompte(42L, "401MOULINS", "Moulins Bertrand",
			MAINTENANT);

	@BeforeEach
	void base() {
		ReflectionTestUtils.setField(cabinet, "id", 7L);
		ReflectionTestUtils.setField(lucie, "id", 3L);
		when(dossiers.findByIdAndCabinetId(42L, 7L)).thenReturn(Optional.of(leroy));
		when(comptes.findByDossierIdOrderByNumero(42L)).thenReturn(List.of(fournisseurs, banque));
		when(comptes.save(any(CompteComptable.class))).thenAnswer(i -> i.getArgument(0));
	}

	// RG1 : comptes du dossier ; RG2 : comptes par défaut des catégories, puis la banque (512).
	@Test
	void consulterLePlan() {
		when(categories.findAllByOrderByOrdre()).thenReturn(List.of(new Categorie("ACHAT", "Achat", "607", 1),
				new Categorie("VENTE", "Vente", "707", 2), new Categorie("FRAIS_BANCAIRES", "Frais bancaires", "627", 3)));

		PlanComptable plan = service.consulter(lucie, 42L).orElseThrow();

		assertEquals(List.of(new VueCompte("401", "Fournisseurs", false, true), new VueCompte("512", "Banque", false,
				true)), plan.comptes());
		assertEquals(List.of(new PlanComptable.CompteParDefaut("ACHAT", "Achat", "607"),
				new PlanComptable.CompteParDefaut("VENTE", "Vente", "707"),
				new PlanComptable.CompteParDefaut("FRAIS_BANCAIRES", "Frais bancaires", "627"),
				new PlanComptable.CompteParDefaut("BANQUE", "Banque", "512")), plan.parDefaut());
	}

	// US-11 RG3 : dossier d'un autre cabinet -> rien, et aucune modification possible.
	@Test
	void dossierInaccessible() {
		assertTrue(service.consulter(lucie, 99L).isEmpty());
		assertEquals(Statut.DOSSIER_INTROUVABLE,
				service.creer(lucie, 99L, new DemandeCompte("401MOULINS", "Moulins Bertrand")).statut());
		assertEquals(Statut.DOSSIER_INTROUVABLE,
				service.changerActivation(lucie, 99L, "401", new DemandeActivation(false)).statut());
		assertEquals(Statut.DOSSIER_INTROUVABLE, service.supprimer(lucie, 99L, "401").statut());
		verify(comptes, never()).save(any());
	}

	// CA1 : Lucie crée 401MOULINS « Moulins Bertrand », actif et choisissable dans une écriture.
	@Test
	void ca1SousCompteCree() {
		ResultatCompte resultat = service.creer(lucie, 42L, new DemandeCompte("401moulins", " Moulins Bertrand "));

		assertEquals(Statut.CREE, resultat.statut());
		assertEquals(new VueCompte("401MOULINS", "Moulins Bertrand", true, true), resultat.compte());
		verify(comptes).save(any(CompteComptable.class));
	}

	// CA2 : 9001 -> « Numéro de compte invalide », rien n'est enregistré.
	@Test
	void ca2NumeroInvalide() {
		ResultatCompte resultat = service.creer(lucie, 42L, new DemandeCompte("9001", "Test"));

		assertEquals(Statut.INVALIDE, resultat.statut());
		assertEquals(Map.of("numero", ValidationCompte.NUMERO_INVALIDE), resultat.erreurs());
		verify(comptes, never()).save(any());
	}

	@Test
	void numeroDejaDansLePlan() {
		when(comptes.existsByDossierIdAndNumero(42L, "401MOULINS")).thenReturn(true);

		assertEquals(Statut.DEJA_EXISTANT,
				service.creer(lucie, 42L, new DemandeCompte("401MOULINS", "Moulins Bertrand")).statut());
		verify(comptes, never()).save(any());
	}

	// RG3 : désactiver puis réactiver un compte du plan général.
	@Test
	void desactiverEtReactiver() {
		when(comptes.findByDossierIdAndNumero(42L, "512")).thenReturn(Optional.of(banque));

		ResultatCompte desactive = service.changerActivation(lucie, 42L, "512", new DemandeActivation(false));
		assertEquals(Statut.MODIFIE, desactive.statut());
		assertFalse(desactive.compte().actif());

		assertTrue(service.changerActivation(lucie, 42L, "512", new DemandeActivation(true)).compte().actif());
	}

	@Test
	void activationSansValeurOuCompteAbsent() {
		assertEquals(Statut.INVALIDE, service.changerActivation(lucie, 42L, "512", new DemandeActivation(null))
				.statut());
		assertEquals(Statut.INVALIDE, service.changerActivation(lucie, 42L, "512", null).statut());
		assertEquals(Statut.COMPTE_INTROUVABLE,
				service.changerActivation(lucie, 42L, "999", new DemandeActivation(false)).statut());
	}

	// RG3 : un sous-compte inutilisé se supprime ; un compte du plan général ou utilisé dans une écriture
	// (US-56) se désactive seulement.
	@Test
	void suppression() {
		when(comptes.findByDossierIdAndNumero(42L, "401MOULINS")).thenReturn(Optional.of(moulins));
		when(comptes.findByDossierIdAndNumero(42L, "401")).thenReturn(Optional.of(fournisseurs));

		assertEquals(Statut.SUPPRIME, service.supprimer(lucie, 42L, "401moulins").statut());
		verify(comptes).delete(moulins);

		assertEquals(Statut.COMPTE_GENERAL, service.supprimer(lucie, 42L, "401").statut());
		verify(comptes, never()).delete(fournisseurs);

		assertEquals(Statut.COMPTE_INTROUVABLE, service.supprimer(lucie, 42L, "4019").statut());

		when(comptes.utiliseDansUneEcriture(42L, "401MOULINS")).thenReturn(true);
		assertEquals(Statut.COMPTE_UTILISE, service.supprimer(lucie, 42L, "401MOULINS").statut());
		verify(comptes, times(1)).delete(moulins);
	}

	@Test
	void entites() {
		assertEquals(42L, moulins.getDossierId());
		assertEquals(MAINTENANT, moulins.getCreeLe());
		assertNull(moulins.getId());
		Categorie achat = new Categorie("ACHAT", "Achat", "607", 1);
		assertEquals(1, achat.getOrdre());
		assertNull(achat.getId());
	}

}
