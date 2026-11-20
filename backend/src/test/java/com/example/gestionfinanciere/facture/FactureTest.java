package com.example.gestionfinanciere.facture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

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

	// US-13 : fichier d'origine, copié pour ne jamais être modifié de l'extérieur ; jamais affiché.
	@Test
	void fichierDOrigine() {
		byte[] contenu = { 1, 2, 3 };
		Facture facture = new Facture(dossier, Facture.Sens.ACHAT, "a.pdf", "application/pdf", MAINTENANT, jean,
				contenu);
		contenu[0] = 9;
		assertArrayEquals(new byte[] { 1, 2, 3 }, facture.getContenu());
		assertNull(new Facture(dossier, Facture.Sens.ACHAT, "a.pdf", "application/pdf", MAINTENANT, jean, null)
				.getContenu());

		FichierFacture fichier = FichierFacture.de(facture);
		assertArrayEquals(new byte[] { 1, 2, 3 }, fichier.contenu());
		assertEquals(fichier, new FichierFacture("a.pdf", "application/pdf", new byte[] { 1, 2, 3 }));
		assertEquals(fichier.hashCode(), FichierFacture.de(facture).hashCode());
		assertNotEquals(fichier, new FichierFacture("a.pdf", "application/pdf", new byte[] { 1 }));
		assertNotEquals(fichier, new FichierFacture("b.pdf", "application/pdf", new byte[] { 1, 2, 3 }));
		assertNotEquals(fichier, new FichierFacture("a.pdf", "image/png", new byte[] { 1, 2, 3 }));
		assertNotEquals(fichier, (Object) "a.pdf");
		assertEquals("FichierFacture[application/pdf, 3 octets]", fichier.toString());
	}

	// US-51 RG2 : passages autorisés depuis chaque statut.
	@Test
	void us51PassagesAutorises() {
		assertEquals(List.of(Facture.Statut.EN_COURS, Facture.Statut.A_COMPLETER), Facture.Statut.RECUE.suivants());
		assertEquals(List.of(Facture.Statut.TRAITEE, Facture.Statut.A_COMPLETER), Facture.Statut.EN_COURS.suivants());
		assertEquals(List.of(Facture.Statut.EN_COURS), Facture.Statut.A_COMPLETER.suivants());
		assertEquals(List.of(), Facture.Statut.TRAITEE.suivants());
	}

	// US-51 RG2 et RG3 : le motif n'est gardé que pour « À compléter » ; un passage interdit ne change rien.
	@Test
	void us51ChangerStatut() {
		Facture facture = new Facture(dossier, Facture.Sens.ACHAT, "f.pdf", "application/pdf", MAINTENANT, jean);

		assertFalse(facture.changerStatut(Facture.Statut.TRAITEE, null));
		assertEquals(Facture.Statut.RECUE, facture.getStatut());

		assertTrue(facture.changerStatut(Facture.Statut.A_COMPLETER, "Facture illisible"));
		assertEquals("Facture illisible", facture.getMotifACompleter());
		assertEquals("Facture illisible", LigneFacture.de(facture).motif());

		assertTrue(facture.changerStatut(Facture.Statut.EN_COURS, "ignoré"));
		assertNull(facture.getMotifACompleter());
		assertTrue(facture.changerStatut(Facture.Statut.TRAITEE, null));
		assertEquals(List.of(), LigneFacture.de(facture).statutsPossibles());
	}

	// US-51 RG4 : un changement de statut garde l'ancien et le nouveau statut, le motif, l'auteur et la date.
	@Test
	void us51ChangementGardeAuteurEtDate() {
		Facture facture = new Facture(dossier, Facture.Sens.ACHAT, "f.pdf", "application/pdf", MAINTENANT, jean);
		ChangementStatutFacture changement = new ChangementStatutFacture(facture, Facture.Statut.RECUE,
				Facture.Statut.A_COMPLETER, "Montant TTC manquant", jean, MAINTENANT);

		assertNull(changement.getId());
		assertSame(facture, changement.getFacture());
		assertEquals(Facture.Statut.RECUE, changement.getAncienStatut());
		assertEquals(Facture.Statut.A_COMPLETER, changement.getNouveauStatut());
		assertEquals("Montant TTC manquant", changement.getMotif());
		assertSame(jean, changement.getModifiePar());
		assertEquals(MAINTENANT, changement.getModifieLe());
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
