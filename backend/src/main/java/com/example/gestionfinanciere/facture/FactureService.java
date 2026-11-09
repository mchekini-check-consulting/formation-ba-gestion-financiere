package com.example.gestionfinanciere.facture;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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

// US-12 Factures d'un dossier, US-13 aperçu et téléchargement. L'appelant vérifie avant que la
// personne connectée est un collaborateur d'un cabinet ; ici, le dossier doit être celui de son cabinet.
@Service
@Loggable
public class FactureService {

	private static final String CHAMP_DOSSIER = "dossierId";
	private static final String CHAMP_FACTURE = "factureId";
	private static final String PDF = "application/pdf";
	private static final String ELECTRICITE = "facture-electricite-octobre.pdf";
	private static final String TICKET = "ticket-fournitures.png";
	private static final String VENTE = "facture-client-0042.pdf";

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
			refus("facture.liste", collaborateur, dossierId, null);
			return Optional.empty();
		}
		List<LigneFacture> lignes = factures.findByDossierIdAndSensOrderByDeposeeLeDescIdDesc(dossierId, sens)
				.stream().map(LigneFacture::de).toList();
		EvenementMetier.de("facture.liste", "consultee").avec(collaborateur.identifiantsPourLogs())
				.avec(CHAMP_DOSSIER, dossierId).avec("sens", sens).avec("nombre", lignes.size()).info();
		return Optional.of(new ListeFactures(lignes, modeTest));
	}

	// US-13 : nom et type du fichier, pour la page d'aperçu ; vide sans droits sur le dossier.
	@Transactional(readOnly = true)
	public Optional<VueFacture> consulter(Compte collaborateur, Long dossierId, Long factureId) {
		Optional<Facture> facture = factureDuCabinet(collaborateur, dossierId, factureId);
		if (facture.isEmpty()) {
			refus("facture.consultation", collaborateur, dossierId, factureId);
		}
		return facture.map(VueFacture::de);
	}

	/**
	 * US-13 RG1 et RG2 : fichier d'origine, pour l'aperçu ou le téléchargement. Quelqu'un du
	 * cabinet l'ouvre : le badge « Nouvelle » disparaît (US-12 RG3). Vide sans droits sur le
	 * dossier (RG3) ou si la facture n'a pas de fichier. Jamais le contenu dans les logs.
	 */
	@Transactional
	public Optional<FichierFacture> ouvrir(Compte collaborateur, Long dossierId, Long factureId,
			boolean telechargement) {
		String event = telechargement ? "facture.telechargement" : "facture.apercu";
		Optional<Facture> facture = factureDuCabinet(collaborateur, dossierId, factureId)
				.filter(trouvee -> trouvee.getContenu() != null);
		if (facture.isEmpty()) {
			refus(event, collaborateur, dossierId, factureId);
			return Optional.empty();
		}
		facture.get().marquerOuverte(horloge.instant());
		EvenementMetier.de(event, "ouverte").avec(collaborateur.identifiantsPourLogs()).avec(CHAMP_DOSSIER, dossierId)
				.avec(CHAMP_FACTURE, factureId).info();
		return facture.map(FichierFacture::de);
	}

	/**
	 * Mode test seulement : le dépôt de factures (US-15) n'existe pas encore. Ajoute au dossier
	 * deux factures fournisseurs (dont une déjà ouverte) et une facture client, déposées par le
	 * collaborateur, avec un vrai fichier (US-13). Faux si le dossier n'est pas celui de son cabinet.
	 */
	@Transactional
	public boolean ajouterExemples(Compte collaborateur, Long dossierId) {
		Optional<Dossier> dossier = dossierDuCabinet(collaborateur, dossierId);
		if (!modeTest || dossier.isEmpty()) {
			refus("facture.exemples", collaborateur, dossierId, null);
			return false;
		}
		Instant maintenant = horloge.instant();
		Facture electricite = new Facture(dossier.get(), Facture.Sens.ACHAT, ELECTRICITE, PDF,
				maintenant.minus(Duration.ofDays(3)), collaborateur, exemple(ELECTRICITE));
		electricite.marquerOuverte(maintenant.minus(Duration.ofDays(2)));
		factures.saveAll(List.of(electricite,
				new Facture(dossier.get(), Facture.Sens.ACHAT, TICKET, "image/png",
						maintenant.minus(Duration.ofHours(5)), collaborateur, exemple(TICKET)),
				new Facture(dossier.get(), Facture.Sens.VENTE, VENTE, PDF, maintenant.minus(Duration.ofDays(1)),
						collaborateur, exemple(VENTE))));
		EvenementMetier.de("facture.exemples", "ajoutees").avec(collaborateur.identifiantsPourLogs())
				.avec(CHAMP_DOSSIER, dossierId).avec("nombre", 3).info();
		return true;
	}

	// Fichiers d'exemple rangés avec l'application (src/main/resources/exemples).
	static byte[] exemple(String nom) {
		try (InputStream fichier = FactureService.class.getResourceAsStream("/exemples/" + nom)) {
			if (fichier == null) {
				throw new IllegalStateException("fichier d'exemple absent : " + nom);
			}
			return fichier.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private Optional<Dossier> dossierDuCabinet(Compte collaborateur, Long dossierId) {
		return dossiers.findByIdAndCabinetId(dossierId, collaborateur.getCabinet().getId());
	}

	private Optional<Facture> factureDuCabinet(Compte collaborateur, Long dossierId, Long factureId) {
		return dossierDuCabinet(collaborateur, dossierId)
				.flatMap(dossier -> factures.findByIdAndDossierId(factureId, dossier.getId()));
	}

	// Refus d'accès : warn.
	private static void refus(String event, Compte collaborateur, Long dossierId, Long factureId) {
		EvenementMetier.de(event, "introuvable").avec(collaborateur.identifiantsPourLogs())
				.avec(CHAMP_DOSSIER, dossierId).avec(CHAMP_FACTURE, factureId).warn();
	}

}
