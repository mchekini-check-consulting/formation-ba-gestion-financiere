package com.example.gestionfinanciere.facture;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// US-12 RG2 : une ligne de la liste des factures d'un dossier ; RG3 : nouvelle tant que personne
// du cabinet ne l'a ouverte. US-51 : motif du statut « À compléter » (RG3) et seuls statuts
// proposés depuis le statut actuel (RG2, CA4). US-52 RG4 : source (dépôt du dirigeant ou import du cabinet).
// US-53 : statut de la lecture et doute sur un champ lu ; tiers, date d'émission et TTC lus ou corrigés
// (colonnes de la maquette du sprint 2), vides tant qu'ils ne sont pas connus.
public record LigneFacture(Long id, String nomFichier, Instant deposeeLe, String deposeePar, Facture.Statut statut,
		boolean nouvelle, String motif, List<Facture.Statut> statutsPossibles, Facture.Source source,
		Facture.StatutLecture lecture, boolean lectureAVerifier, String nomTiers, LocalDate dateEmission,
		BigDecimal montantTtc) {

	static LigneFacture de(Facture facture) {
		return new LigneFacture(facture.getId(), facture.getNomFichier(), facture.getDeposeeLe(),
				facture.getDeposeePar().getNomAffiche(), facture.getStatut(), facture.getOuverteLe() == null,
				facture.getMotifACompleter(), facture.getStatut().suivants(), facture.getSource(),
				facture.getLectureStatut(), facture.isLectureAVerifier(), facture.getNomTiers(),
				facture.getDateEmission(), facture.getMontantTtc());
	}

}
