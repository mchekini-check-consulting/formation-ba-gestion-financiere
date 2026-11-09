package com.example.gestionfinanciere.facture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.dossier.Dossier;
import com.example.gestionfinanciere.equipe.Invitation;

// V12 : facture d'un dossier, compte du dirigeant rattaché au dossier, invitation annulable.
class FactureTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-08T09:00:00Z");

	private final Cabinet cabinet = new Cabinet("Cabinet Test", "123456789", 120, null, MAINTENANT, MAINTENANT);
	private final Compte jean = Compte.administrateurDe(cabinet, "jean@test.fr", "x", "Jean Dupont");
	private final Dossier dossier = new Dossier(cabinet, "Fleurs Martin EURL", "812345679", jean, MAINTENANT);

	@Test
	void factureRecueEtNouvelleTantQuePersonneNeLAOuverte() {
		Facture facture = new Facture(dossier, Facture.Sens.ACHAT, "facture-edf.pdf", "application/pdf", MAINTENANT,
				jean);

		assertSame(dossier, facture.getDossier());
		assertEquals(Facture.Sens.ACHAT, facture.getSens());
		assertEquals("facture-edf.pdf", facture.getNomFichier());
		assertEquals("application/pdf", facture.getTypeFichier());
		assertEquals(MAINTENANT, facture.getDeposeeLe());
		assertSame(jean, facture.getDeposeePar());
		assertEquals(Facture.Statut.RECUE, facture.getStatut());
		assertNull(facture.getOuverteLe());
		assertNull(facture.getId());
	}

	// US-12 RG3 : la première ouverture fait disparaître le badge « Nouvelle » ; elle n'est pas écrasée.
	@Test
	void ouverteUneSeuleFois() {
		Facture facture = new Facture(dossier, Facture.Sens.VENTE, "v.pdf", "application/pdf", MAINTENANT, jean);
		facture.marquerOuverte(MAINTENANT.plusSeconds(10));
		facture.marquerOuverte(MAINTENANT.plusSeconds(20));
		assertEquals(MAINTENANT.plusSeconds(10), facture.getOuverteLe());
		assertFalse(LigneFacture.de(facture).nouvelle());
	}

	@Test
	void dirigeantInviteRattacheASonDossierEtInactif() {
		Compte sophie = Compte.dirigeantInvite(42L, "sophie@test.fr", "x", "Sophie Martin");

		assertEquals(42L, sophie.getDossierId());
		assertEquals(Role.DIRIGEANT, sophie.getRole());
		assertFalse(sophie.isActif());
		assertNull(sophie.getCabinet());
		assertNull(jean.getDossierId());
	}

	@Test
	void invitationNonAnnuleeALaCreation() {
		Invitation invitation = new Invitation(jean, jean, "empreinte", MAINTENANT, MAINTENANT.plusSeconds(60));
		assertNull(invitation.getAnnuleeLe());
	}

}
