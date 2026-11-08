package com.example.gestionfinanciere.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.gestionfinanciere.cabinet.ResultatCodeInscription.Statut;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.CompteRepository;
import com.example.gestionfinanciere.compte.HorlogeReglable;
import com.example.gestionfinanciere.compte.Jetons;
import com.example.gestionfinanciere.compte.ResultatRenvoi;
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.notification.EnvoiEmail;
import com.example.gestionfinanciere.notification.EnvoiImpossibleException;

class InscriptionServiceTest {

	private static final Instant DEPART = Instant.parse("2026-11-05T09:00:00Z");

	private final PasswordEncoder encodeur = new BCryptPasswordEncoder(4);
	private final InscriptionRepository inscriptions = mock(InscriptionRepository.class);
	private final CabinetRepository cabinets = mock(CabinetRepository.class);
	private final CompteRepository comptes = mock(CompteRepository.class);
	// Le service d'envoi d'emails est remplacé par un bouchon (mock).
	private final EnvoiEmail email = mock(EnvoiEmail.class);
	private final HorlogeReglable horloge = new HorlogeReglable(DEPART);
	private final InscriptionService service = new InscriptionService(inscriptions, cabinets, comptes, encodeur,
			horloge, email, true);

	// Inscription enregistrée par demarrer(), retrouvée ensuite par son défi.
	private final AtomicReference<Inscription> enregistree = new AtomicReference<>();

	@BeforeEach
	void base() {
		when(comptes.findByEmail(anyString())).thenReturn(Optional.empty());
		when(inscriptions.save(any(Inscription.class))).thenAnswer(i -> {
			enregistree.set(i.getArgument(0));
			return i.getArgument(0);
		});
		when(inscriptions.findByEmpreinteDefi(anyString())).thenAnswer(i -> Optional.ofNullable(enregistree.get())
				.filter(insc -> insc.getEmpreinteDefi().equals(i.getArgument(0))));
		when(cabinets.save(any(Cabinet.class))).thenAnswer(i -> i.getArgument(0));
		when(comptes.save(any(Compte.class))).thenAnswer(i -> i.getArgument(0));
	}

	private ResultatInscription demarrer() {
		return service.demarrer(ValidationInscriptionTest.claire());
	}

	private String defi() {
		return demarrer().defi().defi();
	}

	@Test
	void etape1EnvoieLeCodeALEmailSaisi() {
		ResultatInscription resultat = demarrer();
		assertEquals(ResultatInscription.Statut.CODE_ENVOYE, resultat.statut());
		assertEquals("c•••••@cabinet-test.fr", resultat.defi().emailMasque());
		String code = resultat.defi().codeDeTest();
		assertTrue(code.matches("\\d{6}"));
		verify(email).envoyer(eq("claire.martin@cabinet-test.fr"), eq(InscriptionService.SUJET), contains(code));

		Inscription i = enregistree.get();
		assertEquals("claire.martin@cabinet-test.fr", i.getEmail());
		assertEquals("123456789", i.getSiren());
		assertEquals(120, i.getNombreDossiersPrevus());
		assertEquals(DEPART, i.getCguAccepteesLe());
		assertEquals(DEPART.plus(Duration.ofMinutes(5)), i.getExpireLe());
		// Aucun secret en clair.
		assertNotEquals("Pointeo2026!!", i.getMotDePasseHash());
		assertTrue(encodeur.matches("Pointeo2026!!", i.getMotDePasseHash()));
		assertEquals(Jetons.empreinte(resultat.defi().defi()), i.getEmpreinteDefi());
		assertTrue(encodeur.matches(code, i.getEmpreinteCode()));
	}

	@Test
	void emailImpossibleRemonteLErreur() {
		doThrow(new EnvoiImpossibleException("api", null)).when(email).envoyer(anyString(), anyString(), anyString());
		assertThrows(EnvoiImpossibleException.class, this::demarrer);
	}

	@Test
	void horsModeTestLeCodeNEstPasRenvoye() {
		InscriptionService production = new InscriptionService(inscriptions, cabinets, comptes, encodeur, horloge,
				email, false);
		assertNull(production.demarrer(ValidationInscriptionTest.claire()).defi().codeDeTest());
	}

	@Test
	void formulaireInvalideRienNEstEnvoye() {
		ResultatInscription resultat = service.demarrer(
				new DemandeInscription("Cabinet Test", "123", "Claire", "Martin", "claire@test.fr",
						"Pointeo2026!!", 120, null, false));
		assertEquals(ResultatInscription.Statut.INVALIDE, resultat.statut());
		assertEquals(2, resultat.erreurs().size());
		verify(email, never()).envoyer(anyString(), anyString(), anyString());
		verify(inscriptions, never()).save(any());
	}

	@Test
	void ca2SirenDejaUtiliseRefuse() {
		when(cabinets.existsBySiren("123456789")).thenReturn(true);
		assertEquals(ResultatInscription.Statut.SIREN_DEJA_UTILISE, demarrer().statut());
		verify(email, never()).envoyer(anyString(), anyString(), anyString());
	}

	@Test
	void emailDejaUtiliseRefuse() {
		when(comptes.findByEmail("claire.martin@cabinet-test.fr")).thenReturn(Optional.of(mock(Compte.class)));
		assertEquals(ResultatInscription.Statut.EMAIL_DEJA_UTILISE, demarrer().statut());
		verify(email, never()).envoyer(anyString(), anyString(), anyString());
	}

	@Test
	void ca1BonCodeCreeLeCabinetEtSonAdministratrice() {
		ResultatInscription etape1 = demarrer();
		horloge.avancer(Duration.ofMinutes(2));
		ResultatCodeInscription resultat = service.validerCode(etape1.defi().defi(), etape1.defi().codeDeTest());

		assertEquals(Statut.CABINET_CREE, resultat.statut());
		Compte claire = resultat.compte();
		assertEquals("claire.martin@cabinet-test.fr", claire.getEmail());
		assertEquals("Claire Martin", claire.getNomAffiche());
		assertEquals(Role.COLLABORATEUR, claire.getRole());
		assertTrue(claire.isAdministrateur());
		assertTrue(claire.isActif());
		assertTrue(encodeur.matches("Pointeo2026!!", claire.getMotDePasseHash()));

		ArgumentCaptor<Cabinet> cabinet = ArgumentCaptor.forClass(Cabinet.class);
		verify(cabinets).save(cabinet.capture());
		assertSame(cabinet.getValue(), claire.getCabinet());
		assertEquals("Cabinet Test", cabinet.getValue().getNom());
		assertEquals("123456789", cabinet.getValue().getSiren());
		assertEquals(120, cabinet.getValue().getNombreDossiersPrevus());
		assertEquals(DEPART, cabinet.getValue().getCguAccepteesLe());
		assertEquals(DEPART.plus(Duration.ofMinutes(2)), cabinet.getValue().getCreeLe());
		assertTrue(enregistree.get().isTerminee());

		// Le code ne sert qu'une fois.
		assertEquals(Statut.DEMANDE_INVALIDE,
				service.validerCode(etape1.defi().defi(), etape1.defi().codeDeTest()).statut());
	}

	@Test
	void codeFauxPuisTropDeCodesFaux() {
		String defi = defi();
		ResultatCodeInscription premier = service.validerCode(defi, "abc");
		assertEquals(Statut.FAUX, premier.statut());
		assertEquals(2, premier.tentativesRestantes());
		ResultatCodeInscription deuxieme = service.validerCode(defi, "000000");
		assertEquals(Statut.FAUX, deuxieme.statut());
		assertEquals(1, deuxieme.tentativesRestantes());
		assertEquals(Statut.TROP_DE_CODES_FAUX, service.validerCode(defi, "000001").statut());
		assertTrue(enregistree.get().isTerminee());
		verify(cabinets, never()).save(any());
	}

	@Test
	void codeExpireApres5Minutes() {
		ResultatInscription etape1 = demarrer();
		horloge.avancer(Duration.ofMinutes(5));
		ResultatCodeInscription resultat = service.validerCode(etape1.defi().defi(), etape1.defi().codeDeTest());
		assertEquals(Statut.EXPIRE, resultat.statut());
		assertEquals(3, resultat.tentativesRestantes());
	}

	@Test
	void defiInconnuOuVide() {
		assertEquals(Statut.DEMANDE_INVALIDE, service.validerCode(null, "123456").statut());
		assertEquals(Statut.DEMANDE_INVALIDE, service.validerCode("inconnu", "123456").statut());
	}

	@Test
	void sirenPrisEntreLeFormulaireEtLeCode() {
		ResultatInscription etape1 = demarrer();
		when(cabinets.existsBySiren("123456789")).thenReturn(true);
		assertEquals(Statut.SIREN_DEJA_UTILISE,
				service.validerCode(etape1.defi().defi(), etape1.defi().codeDeTest()).statut());
		verify(cabinets, never()).save(any());
	}

	@Test
	void emailPrisEntreLeFormulaireEtLeCode() {
		ResultatInscription etape1 = demarrer();
		when(comptes.findByEmail("claire.martin@cabinet-test.fr")).thenReturn(Optional.of(mock(Compte.class)));
		assertEquals(Statut.EMAIL_DEJA_UTILISE,
				service.validerCode(etape1.defi().defi(), etape1.defi().codeDeTest()).statut());
		verify(comptes, never()).save(any());
	}

	@Test
	void renvoiAvant60SecondesRefuse() {
		String defi = defi();
		horloge.avancer(Duration.ofMillis(20_500));
		ResultatRenvoi resultat = service.renvoyer(defi);
		assertEquals(ResultatRenvoi.Statut.TROP_TOT, resultat.statut());
		assertEquals(40, resultat.secondesRestantes());
	}

	@Test
	void renvoiApres60SecondesRemplaceLeCode() {
		ResultatInscription etape1 = demarrer();
		String ancien = etape1.defi().codeDeTest();
		horloge.avancer(Duration.ofSeconds(60));
		ResultatRenvoi resultat = service.renvoyer(etape1.defi().defi());
		assertEquals(ResultatRenvoi.Statut.RENVOYE, resultat.statut());
		assertEquals(DEPART.plus(Duration.ofMinutes(6)), enregistree.get().getExpireLe());
		assertTrue(encodeur.matches(resultat.codeDeTest(), enregistree.get().getEmpreinteCode()));
		if (!ancien.equals(resultat.codeDeTest())) {
			assertFalse(encodeur.matches(ancien, enregistree.get().getEmpreinteCode()));
		}
	}

	@Test
	void renvoiDemandeInconnue() {
		assertEquals(ResultatRenvoi.Statut.DEMANDE_INVALIDE, service.renvoyer(" ").statut());
	}

}
