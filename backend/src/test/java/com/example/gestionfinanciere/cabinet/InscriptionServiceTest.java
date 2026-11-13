package com.example.gestionfinanciere.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.example.gestionfinanciere.cabinet.ResultatInscription.Statut;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.CompteRepository;
import com.example.gestionfinanciere.compte.HorlogeReglable;
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.logging.EvenementMetier;

class InscriptionServiceTest {

	private static final Instant DEPART = Instant.parse("2026-11-05T09:00:00Z");

	private final PasswordEncoder encodeur = new BCryptPasswordEncoder(4);
	private final CabinetRepository cabinets = mock(CabinetRepository.class);
	private final CompteRepository comptes = mock(CompteRepository.class);
	private final HorlogeReglable horloge = new HorlogeReglable(DEPART);
	private final InscriptionService service = new InscriptionService(cabinets, comptes, encodeur, horloge);

	@BeforeEach
	void base() {
		when(comptes.findByEmail(anyString())).thenReturn(Optional.empty());
		when(cabinets.save(any(Cabinet.class))).thenAnswer(i -> i.getArgument(0));
		when(comptes.save(any(Compte.class))).thenAnswer(i -> i.getArgument(0));
	}

	private ResultatInscription creer() {
		return service.creer(ValidationInscriptionTest.claire());
	}

	private static DemandeInscription incomplete() {
		return new DemandeInscription("Cabinet Test", "123", "Claire", "Martin", "claire@test.fr", "Pointeo2026!!",
				120, null, false);
	}

	@Test
	void ca1FormulaireValideCreeLeCabinetEtSonAdministratrice() {
		ResultatInscription resultat = creer();

		assertEquals(Statut.CABINET_CREE, resultat.statut());
		Compte claire = resultat.compte();
		assertEquals("claire.martin@cabinet-test.fr", claire.getEmail());
		assertEquals("Claire Martin", claire.getNomAffiche());
		assertEquals(Role.COLLABORATEUR, claire.getRole());
		assertTrue(claire.isAdministrateur());
		assertTrue(claire.isActif());
		// Aucun mot de passe en clair.
		assertNotEquals("Pointeo2026!!", claire.getMotDePasseHash());
		assertTrue(encodeur.matches("Pointeo2026!!", claire.getMotDePasseHash()));

		ArgumentCaptor<Cabinet> cabinet = ArgumentCaptor.forClass(Cabinet.class);
		verify(cabinets).save(cabinet.capture());
		assertSame(cabinet.getValue(), claire.getCabinet());
		assertEquals("Cabinet Test", cabinet.getValue().getNom());
		assertEquals("123456789", cabinet.getValue().getSiren());
		assertEquals(120, cabinet.getValue().getNombreDossiersPrevus());
		assertNull(cabinet.getValue().getCodeParrainageSaisi());
		assertEquals(DEPART, cabinet.getValue().getCguAccepteesLe());
		assertEquals(DEPART, cabinet.getValue().getCreeLe());
	}

	// PTO-84 CA1, CA2, CA3, CA5 : chaque création est journalisée, sans email, nom, SIREN ni mot de passe.
	@Test
	void logsMetierSansDonneePersonnelle() {
		Logger logger = (Logger) LoggerFactory.getLogger(EvenementMetier.class);
		ListAppender<ILoggingEvent> logs = new ListAppender<>();
		logs.start();
		logger.addAppender(logs);
		try {
			service.creer(incomplete());
			when(cabinets.existsBySiren("123456789")).thenReturn(true);
			creer();
			when(cabinets.existsBySiren("123456789")).thenReturn(false);
			creer();

			List<String> resultats = logs.list.stream()
					.map(e -> e.getMDCPropertyMap().get("event") + "=" + e.getMDCPropertyMap().get("outcome")).toList();
			assertEquals(List.of("inscription.creee=invalide", "inscription.creee=siren_deja_utilise",
					"inscription.creee=cabinet_cree"), resultats);
			assertEquals("cguAcceptees,siren", logs.list.get(0).getMDCPropertyMap().get("champsInvalides"));
			assertEquals(Level.WARN, logs.list.get(1).getLevel());
			assertEquals("ADMINISTRATEUR", logs.list.get(2).getMDCPropertyMap().get("roleCabinet"));
			assertEquals(Level.INFO, logs.list.get(2).getLevel());

			List<String> interdits = List.of("claire", "Claire", "Martin", "123456789", "123 456 789", "Pointeo2026");
			for (ILoggingEvent log : logs.list) {
				String ligne = log.getFormattedMessage() + " " + log.getMDCPropertyMap();
				interdits.forEach(mot -> assertFalse(ligne.contains(mot), () -> "donnée personnelle dans : " + ligne));
			}
		} finally {
			logger.detachAppender(logs);
		}
	}

	@Test
	void formulaireInvalideRienNEstCree() {
		ResultatInscription resultat = service.creer(incomplete());
		assertEquals(Statut.INVALIDE, resultat.statut());
		assertEquals(2, resultat.erreurs().size());
		assertNull(resultat.compte());
		verify(cabinets, never()).save(any());
		verify(comptes, never()).save(any());
	}

	@Test
	void ca2SirenDejaUtiliseRefuse() {
		when(cabinets.existsBySiren("123456789")).thenReturn(true);
		assertEquals(Statut.SIREN_DEJA_UTILISE, creer().statut());
		verify(cabinets, never()).save(any());
	}

	@Test
	void emailDejaUtiliseRefuse() {
		when(comptes.findByEmail("claire.martin@cabinet-test.fr")).thenReturn(Optional.of(mock(Compte.class)));
		assertEquals(Statut.EMAIL_DEJA_UTILISE, creer().statut());
		verify(cabinets, never()).save(any());
		verify(comptes, never()).save(any());
	}

}
