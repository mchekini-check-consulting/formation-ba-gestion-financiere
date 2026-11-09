package com.example.gestionfinanciere.dossier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.cabinet.ValidationInscription;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.CompteRepository;
import com.example.gestionfinanciere.compte.HorlogeReglable;
import com.example.gestionfinanciere.compte.Jetons;
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.equipe.Invitation;
import com.example.gestionfinanciere.equipe.InvitationRepository;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.JournalCapture;
import com.example.gestionfinanciere.notification.EnvoiEmail;
import com.example.gestionfinanciere.notification.EnvoiImpossibleException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

class InvitationDirigeantServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-09T09:00:00Z");

	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final CompteRepository comptes = mock(CompteRepository.class);
	private final InvitationRepository invitations = mock(InvitationRepository.class);
	private final EnvoiEmail emails = mock(EnvoiEmail.class);

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, MAINTENANT, MAINTENANT);
	private final Compte jean = Compte.administrateurDe(cabinet, "jean.dupont@cabinet-test.fr", "x", "Jean Dupont");
	private final Dossier boulangerie = new Dossier(cabinet, "Boulangerie Leroy SARL", "812345678", jean, MAINTENANT);

	private InvitationDirigeantService service(boolean modeTest) {
		return new InvitationDirigeantService(dossiers, comptes, invitations, new BCryptPasswordEncoder(4),
				new HorlogeReglable(MAINTENANT), emails, "https://frontend.test", modeTest);
	}

	// RG1 : Jean invite Sophie Martin, dirigeante de la Boulangerie Leroy.
	static DemandeInvitationDirigeant sophie() {
		return new DemandeInvitationDirigeant(" Sophie ", "Martin", " Sophie.Martin@Boulangerie-Test.fr ");
	}

	@BeforeEach
	void base() {
		ReflectionTestUtils.setField(cabinet, "id", 7L);
		ReflectionTestUtils.setField(jean, "id", 3L);
		ReflectionTestUtils.setField(boulangerie, "id", 42L);
		when(dossiers.findByIdAndCabinetId(42L, 7L)).thenReturn(Optional.of(boulangerie));
		when(comptes.findByEmail(anyString())).thenReturn(Optional.empty());
		when(comptes.save(any(Compte.class))).thenAnswer(i -> {
			Compte compte = i.getArgument(0);
			ReflectionTestUtils.setField(compte, "id", 50L);
			return compte;
		});
		when(invitations.save(any(Invitation.class))).thenAnswer(i -> i.getArgument(0));
	}

	@Test
	void rg1Rg3InvitationEnvoyeeAuNomDuCabinet() {
		ResultatInvitationDirigeant resultat = service(true).inviter(jean, 42L, sophie());

		assertEquals(ResultatInvitationDirigeant.Statut.ENVOYEE, resultat.statut());
		ArgumentCaptor<Compte> compte = ArgumentCaptor.forClass(Compte.class);
		verify(comptes).save(compte.capture());
		Compte dirigeante = compte.getValue();
		assertEquals(Role.DIRIGEANT, dirigeante.getRole());
		assertEquals(42L, dirigeante.getDossierId());
		assertFalse(dirigeante.isActif()); // RG3 : Invité, puis Actif à l'activation (US-08)
		assertEquals("Sophie Martin", dirigeante.getNomAffiche());
		assertEquals("sophie.martin@boulangerie-test.fr", dirigeante.getEmail());

		// RG2 : lien valable 7 jours ; seule l'empreinte du jeton est stockée.
		ArgumentCaptor<Invitation> invitation = ArgumentCaptor.forClass(Invitation.class);
		verify(invitations).save(invitation.capture());
		assertEquals(MAINTENANT.plusSeconds(7 * 24 * 3600), invitation.getValue().getExpireLe());
		assertEquals(Jetons.empreinte(resultat.jetonDeTest()), invitation.getValue().getEmpreinteJeton());
		assertNotEquals(resultat.jetonDeTest(), invitation.getValue().getEmpreinteJeton());

		ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
		verify(emails).envoyer(eq("sophie.martin@boulangerie-test.fr"),
				eq("Cabinet Test vous invite sur votre espace client Pointéo"), corps.capture());
		assertTrue(corps.getValue().contains("Le cabinet Cabinet Test vous invite"));
		assertTrue(corps.getValue().contains("Boulangerie Leroy SARL"));
		assertTrue(corps.getValue().contains("https://frontend.test/activer-compte?jeton=" + resultat.jetonDeTest()));
		assertTrue(corps.getValue().contains("valable 7 jours"));

		// Hors mode test, le lien ne revient jamais à l'écran.
		when(comptes.existsByDossierId(42L)).thenReturn(false);
		assertNull(service(false).inviter(jean, 42L, sophie()).jetonDeTest());
	}

	// CA erreur : « Saisissez une adresse email valide » ; prénom et nom obligatoires.
	@Test
	void caErreurEmailInvalide() {
		ResultatInvitationDirigeant resultat = service(true).inviter(jean, 42L,
				new DemandeInvitationDirigeant("", "Martin", "sophie@"));

		assertEquals(ResultatInvitationDirigeant.Statut.INVALIDE, resultat.statut());
		assertEquals(Map.of("prenom", ValidationInscription.OBLIGATOIRE, "email",
				"Saisissez une adresse email valide"), resultat.erreurs());
		assertEquals(Map.of("nom", ValidationInscription.TROP_LONG, "email", "Saisissez une adresse email valide"),
				ValidationInvitationDirigeant.erreurs(new DemandeInvitationDirigeant("Sophie", "M".repeat(101), null)));
		verify(comptes, never()).save(any());
		verify(emails, never()).envoyer(anyString(), anyString(), anyString());
	}

	// RG3 : déjà invité (ou actif) ; adresse d'un autre compte ; dossier d'un autre cabinet.
	@Test
	void refus() {
		assertEquals(ResultatInvitationDirigeant.Statut.INTROUVABLE,
				service(true).inviter(jean, 99L, sophie()).statut());
		when(comptes.findByEmail("sophie.martin@boulangerie-test.fr")).thenReturn(Optional.of(jean));
		assertEquals(ResultatInvitationDirigeant.Statut.EMAIL_DEJA_UTILISE,
				service(true).inviter(jean, 42L, sophie()).statut());
		when(comptes.existsByDossierId(42L)).thenReturn(true);
		assertEquals(ResultatInvitationDirigeant.Statut.ETAT_INCOMPATIBLE,
				service(true).inviter(jean, 42L, sophie()).statut());
		verify(comptes, never()).save(any());
	}

	// RG2 : « Renvoyer l'invitation » : nouveau lien, l'ancien est annulé.
	@Test
	void rg2RenvoiAnnuleLAncienLien() {
		Compte sophie = Compte.dirigeantInvite(42L, "sophie.martin@boulangerie-test.fr", "x", "Sophie Martin");
		ReflectionTestUtils.setField(sophie, "id", 50L);
		Invitation ancienne = new Invitation(sophie, jean, "ancienne", MAINTENANT.minusSeconds(60), MAINTENANT);
		when(comptes.findFirstByDossierIdOrderByIdDesc(42L)).thenReturn(Optional.of(sophie));
		when(invitations.findByCompteIdAndUtiliseeLeIsNullAndAnnuleeLeIsNull(50L)).thenReturn(List.of(ancienne));

		ResultatInvitationDirigeant resultat = service(true).renvoyer(jean, 42L);

		assertEquals(ResultatInvitationDirigeant.Statut.ENVOYEE, resultat.statut());
		assertEquals(MAINTENANT, ancienne.getAnnuleeLe());
		ArgumentCaptor<Invitation> nouvelle = ArgumentCaptor.forClass(Invitation.class);
		verify(invitations).save(nouvelle.capture());
		assertEquals(Jetons.empreinte(resultat.jetonDeTest()), nouvelle.getValue().getEmpreinteJeton());
		verify(emails).envoyer(eq("sophie.martin@boulangerie-test.fr"), anyString(), anyString());

		// Annuler deux fois ne change pas la date.
		ancienne.annuler(MAINTENANT.plusSeconds(10));
		assertEquals(MAINTENANT, ancienne.getAnnuleeLe());
	}

	@Test
	void renvoiImpossibleSansInvitationEnAttente() {
		assertEquals(ResultatInvitationDirigeant.Statut.INTROUVABLE, service(true).renvoyer(jean, 99L).statut());
		// Non invité : aucun compte dirigeant.
		assertEquals(ResultatInvitationDirigeant.Statut.ETAT_INCOMPATIBLE, service(true).renvoyer(jean, 42L).statut());
		// Actif : l'espace est déjà activé.
		Compte active = Compte.dirigeantInvite(42L, "s@test.fr", "x", "Sophie");
		ReflectionTestUtils.setField(active, "actif", true);
		when(comptes.findFirstByDossierIdOrderByIdDesc(42L)).thenReturn(Optional.of(active));
		assertEquals(ResultatInvitationDirigeant.Statut.ETAT_INCOMPATIBLE, service(true).renvoyer(jean, 42L).statut());
		verify(invitations, never()).save(any());
	}

	// Logs : envoi (info), refus (warn), email impossible (error) ; jamais le jeton ni l'email.
	@Test
	void logs() {
		try (JournalCapture journal = JournalCapture.de(EvenementMetier.class)) {
			ResultatInvitationDirigeant resultat = service(true).inviter(jean, 42L, sophie());
			service(true).inviter(jean, 42L, new DemandeInvitationDirigeant("Sophie", "Martin", "x"));
			doThrow(new EnvoiImpossibleException("panne", null)).when(emails).envoyer(anyString(), anyString(),
					anyString());
			assertThrows(EnvoiImpossibleException.class, () -> service(true).inviter(jean, 42L, sophie()));

			ILoggingEvent envoi = journal.seule(Level.INFO);
			assertEquals("dossier.invitation_dirigeant", envoi.getMDCPropertyMap().get("event"));
			assertEquals("envoyee", envoi.getMDCPropertyMap().get("outcome"));
			assertEquals("42", envoi.getMDCPropertyMap().get("dossierId"));
			assertEquals("email", journal.seule(Level.WARN).getMDCPropertyMap().get("champsInvalides"));
			assertEquals("email_impossible", journal.seule(Level.ERROR).getMDCPropertyMap().get("outcome"));
			for (ILoggingEvent ligne : journal.lignes()) {
				String texte = ligne.getFormattedMessage() + ligne.getMDCPropertyMap();
				assertFalse(texte.contains(resultat.jetonDeTest()));
				assertFalse(texte.toLowerCase().contains("sophie"));
			}
		}
	}

}
