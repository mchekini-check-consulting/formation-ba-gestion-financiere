package com.example.gestionfinanciere.dossier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.CompteRepository;
import com.example.gestionfinanciere.compte.HorlogeReglable;
import com.example.gestionfinanciere.compte.RoleCabinet;

class AttributionServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-11T09:00:00Z");

	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final AttributionDossierRepository attributions = mock(AttributionDossierRepository.class);
	private final CompteRepository comptes = mock(CompteRepository.class);
	private final AttributionService service = new AttributionService(dossiers, attributions, comptes,
			new HorlogeReglable(MAINTENANT));

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, MAINTENANT, MAINTENANT);
	private final Compte jean = Compte.administrateurDe(cabinet, "jean@test.fr", "x", "Jean Dupont");
	private final Compte paul = Compte.invite(cabinet, "paul@test.fr", "x", "Paul Associé", RoleCabinet.ASSOCIE);
	private final Compte lucie = Compte.invite(cabinet, "lucie@test.fr", "x", "Lucie Bernard", RoleCabinet.COMPTABLE);
	private final Compte marc = Compte.invite(cabinet, "marc@test.fr", "x", "Marc Petit", RoleCabinet.COMPTABLE);
	private final Dossier garage = new Dossier(cabinet, "Garage Morel SAS", "812345679", jean, MAINTENANT);

	@BeforeEach
	void base() {
		ReflectionTestUtils.setField(cabinet, "id", 7L);
		ReflectionTestUtils.setField(jean, "id", 1L);
		ReflectionTestUtils.setField(paul, "id", 2L);
		ReflectionTestUtils.setField(lucie, "id", 5L);
		ReflectionTestUtils.setField(marc, "id", 6L);
		ReflectionTestUtils.setField(garage, "id", 42L);
		when(dossiers.findByIdAndCabinetId(42L, 7L)).thenReturn(Optional.of(garage));
		when(comptes.findByCabinetIdOrderByNomAfficheAsc(7L)).thenReturn(List.of(jean, lucie, marc, paul));
	}

	// Garage Morel SAS sans comptable : tous les comptables du cabinet, aucun coché ; Jean peut attribuer.
	@Test
	void comptablesDuCabinetSansAttribution() {
		ComptablesDossier vue = service.consulter(jean, 42L).orElseThrow();

		assertEquals(List.of(new ComptablesDossier.Comptable(5L, "Lucie Bernard", false),
				new ComptablesDossier.Comptable(6L, "Marc Petit", false)), vue.comptables());
		assertTrue(vue.modifiable());
		assertTrue(service.consulter(jean, 99L).isEmpty());
	}

	// CA1 : Jean attribue Garage Morel SAS à Lucie ; l'attribution précédente est remplacée.
	@Test
	@SuppressWarnings("unchecked")
	void ca1JeanAttribueLeDossierALucie() {
		when(attributions.findByDossierId(42L)).thenReturn(List.of(new AttributionDossier(garage, lucie, MAINTENANT)));

		ResultatAttribution resultat = service.attribuer(jean, 42L, new DemandeAttribution(List.of(5L, 5L)));

		assertEquals(ResultatAttribution.Statut.ATTRIBUE, resultat.statut());
		assertEquals(List.of(true, false),
				resultat.comptables().comptables().stream().map(ComptablesDossier.Comptable::attribue).toList());
		InOrder ordre = inOrder(attributions);
		ordre.verify(attributions).supprimerPourDossier(42L);
		ArgumentCaptor<Iterable<AttributionDossier>> enregistrees = ArgumentCaptor.forClass(Iterable.class);
		ordre.verify(attributions).saveAll(enregistrees.capture());
		List<AttributionDossier> liste = new ArrayList<>();
		enregistrees.getValue().forEach(liste::add);
		assertEquals(1, liste.size());
		assertEquals(lucie, liste.get(0).getComptable());
		assertEquals(garage, liste.get(0).getDossier());
		assertEquals(MAINTENANT, liste.get(0).getAttribueLe());
	}

	// RG1 : un associé attribue aussi ; liste vide = plus aucun comptable.
	@Test
	void rg1LAssocieAttribueAussi() {
		assertEquals(ResultatAttribution.Statut.ATTRIBUE,
				service.attribuer(paul, 42L, new DemandeAttribution(List.of())).statut());
		verify(attributions).supprimerPourDossier(42L);
	}

	// RG1 : un comptable n'attribue pas les dossiers, même le sien.
	@Test
	void rg1LeComptableNAttribuePas() {
		when(dossiers.estAttribue(42L, 5L)).thenReturn(true);

		ResultatAttribution resultat = service.attribuer(lucie, 42L, new DemandeAttribution(List.of(5L)));

		assertEquals(ResultatAttribution.Statut.INTERDIT, resultat.statut());
		assertNull(resultat.comptables());
		assertFalse(service.consulter(lucie, 42L).orElseThrow().modifiable());
		verify(attributions, never()).supprimerPourDossier(any());
	}

	// RG2 : dossier non attribué à Lucie -> introuvable pour elle.
	@Test
	void rg2DossierNonAttribueIntrouvablePourLeComptable() {
		assertTrue(service.consulter(lucie, 42L).isEmpty());
	}

	@Test
	void dossierIntrouvableOuDemandeInvalide() {
		assertEquals(ResultatAttribution.Statut.INTROUVABLE,
				service.attribuer(jean, 99L, new DemandeAttribution(List.of(5L))).statut());
		assertEquals(ResultatAttribution.Statut.INVALIDE, service.attribuer(jean, 42L, null).statut());
		assertEquals(ResultatAttribution.Statut.INVALIDE,
				service.attribuer(jean, 42L, new DemandeAttribution(null)).statut());
		assertEquals(ResultatAttribution.Statut.INVALIDE,
				service.attribuer(jean, 42L, new DemandeAttribution(Arrays.asList(5L, null))).statut());
		// Jean (administrateur) et un compte inconnu ne sont pas des comptables du cabinet.
		assertEquals(ResultatAttribution.Statut.INVALIDE,
				service.attribuer(jean, 42L, new DemandeAttribution(List.of(1L))).statut());
		assertEquals(ResultatAttribution.Statut.INVALIDE,
				service.attribuer(jean, 42L, new DemandeAttribution(List.of(77L))).statut());
		verify(attributions, never()).supprimerPourDossier(any());
	}

}
