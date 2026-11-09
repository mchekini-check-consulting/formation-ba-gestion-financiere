package com.example.gestionfinanciere.facture;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.dossier.Dossier;
import com.example.gestionfinanciere.dossier.DossierRepository;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.Loggable;

// US-12 Factures d'un dossier. L'appelant vérifie avant que la personne connectée est un
// collaborateur d'un cabinet ; ici, le dossier doit être celui de son cabinet.
@Service
@Loggable
public class FactureService {

	private static final String CHAMP_DOSSIER = "dossierId";

	private final FactureRepository factures;
	private final DossierRepository dossiers;
	private final Clock horloge;
	private final boolean modeTest;

	public FactureService(FactureRepository factures, DossierRepository dossiers, Clock horloge,
			@Value("${pointeo.mode-test:false}") boolean modeTest) {
		this.factures = factures;
		this.dossiers = dossiers;
		this.horloge = horloge;
		this.modeTest = modeTest;
	}

	// RG1 : achats ou ventes du dossier ; vide si le dossier n'existe pas ou est d'un autre cabinet.
	@Transactional(readOnly = true)
	public Optional<ListeFactures> lister(Compte collaborateur, Long dossierId, Facture.Sens sens) {
		Optional<Dossier> dossier = dossierDuCabinet(collaborateur, dossierId);
		if (dossier.isEmpty()) {
			refus("facture.liste", collaborateur, dossierId);
			return Optional.empty();
		}
		List<LigneFacture> lignes = factures.findByDossierIdAndSensOrderByDeposeeLeDescIdDesc(dossierId, sens)
				.stream().map(LigneFacture::de).toList();
		EvenementMetier.de("facture.liste", "consultee").avec(collaborateur.identifiantsPourLogs())
				.avec(CHAMP_DOSSIER, dossierId).avec("sens", sens).avec("nombre", lignes.size()).info();
		return Optional.of(new ListeFactures(lignes, modeTest));
	}

	/**
	 * Mode test seulement : le dépôt de factures (US-15) n'existe pas encore. Ajoute au dossier
	 * deux factures fournisseurs (dont une déjà ouverte) et une facture client, déposées par le
	 * collaborateur. Faux si le dossier n'est pas celui de son cabinet.
	 */
	@Transactional
	public boolean ajouterExemples(Compte collaborateur, Long dossierId) {
		Optional<Dossier> dossier = dossierDuCabinet(collaborateur, dossierId);
		if (!modeTest || dossier.isEmpty()) {
			refus("facture.exemples", collaborateur, dossierId);
			return false;
		}
		Instant maintenant = horloge.instant();
		Facture electricite = new Facture(dossier.get(), Facture.Sens.ACHAT, "facture-electricite-octobre.pdf",
				"application/pdf", maintenant.minus(Duration.ofDays(3)), collaborateur);
		electricite.marquerOuverte(maintenant.minus(Duration.ofDays(2)));
		factures.saveAll(List.of(electricite,
				new Facture(dossier.get(), Facture.Sens.ACHAT, "ticket-fournitures.jpg", "image/jpeg",
						maintenant.minus(Duration.ofHours(5)), collaborateur),
				new Facture(dossier.get(), Facture.Sens.VENTE, "facture-client-0042.pdf", "application/pdf",
						maintenant.minus(Duration.ofDays(1)), collaborateur)));
		EvenementMetier.de("facture.exemples", "ajoutees").avec(collaborateur.identifiantsPourLogs())
				.avec(CHAMP_DOSSIER, dossierId).avec("nombre", 3).info();
		return true;
	}

	private Optional<Dossier> dossierDuCabinet(Compte collaborateur, Long dossierId) {
		return dossiers.findByIdAndCabinetId(dossierId, collaborateur.getCabinet().getId());
	}

	// Refus d'accès : warn.
	private static void refus(String event, Compte collaborateur, Long dossierId) {
		EvenementMetier.de(event, "introuvable").avec(collaborateur.identifiantsPourLogs())
				.avec(CHAMP_DOSSIER, dossierId).warn();
	}

}
