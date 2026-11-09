package com.example.gestionfinanciere.facture;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.SessionController;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.dossier.DossierController;

// US-12 Factures d'un dossier et US-13 aperçu / téléchargement, exposées par le gateway sous /backend/dossiers/{id}/factures ;
// mêmes droits que le dossier (US-11 RG3), session obligatoire (US-07) :
//   GET  /dossiers/{id}/factures?sens=ACHAT|VENTE  factures fournisseurs (achats) ou clients (ventes)
//   GET  /dossiers/{id}/factures/{f}               nom et type du fichier (US-13)
//   GET  /dossiers/{id}/factures/{f}/fichier       fichier d'origine : aperçu, ou ?telechargement=true (US-13)
//   POST /dossiers/{id}/factures/exemples          mode test seulement : factures d'exemple
@RestController
@RequestMapping("/dossiers/{dossierId}/factures")
public class FactureController {

	static final String MESSAGE_SENS_INVALIDE = "Indiquez ACHAT ou VENTE";
	// US-13 RG3 : facture d'un dossier sans droits, inexistante ou sans fichier.
	static final String MESSAGE_FACTURE_INTROUVABLE = "Facture introuvable ou accès non autorisé";

	private final FactureService factures;
	private final SessionService sessions;

	public FactureController(FactureService factures, SessionService sessions) {
		this.factures = factures;
		this.sessions = sessions;
	}

	@GetMapping
	public ResponseEntity<Object> lister(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId, @RequestParam(value = "sens", defaultValue = "ACHAT") String sens) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = DossierController.refus(session);
		if (refus != null) {
			return refus;
		}
		Optional<Facture.Sens> onglet = sens(sens);
		if (onglet.isEmpty()) {
			return ResponseEntity.badRequest().body(new ErreurResponse(MESSAGE_SENS_INVALIDE));
		}
		Optional<Long> numero = DossierController.numero(dossierId);
		if (numero.isEmpty()) {
			return DossierController.introuvable();
		}
		return factures.lister(session.compte(), numero.get(), onglet.get())
				.<ResponseEntity<Object>>map(ResponseEntity::ok).orElseGet(DossierController::introuvable);
	}

	@GetMapping("/{factureId}")
	public ResponseEntity<Object> consulter(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId, @PathVariable String factureId) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = DossierController.refus(session);
		if (refus != null) {
			return refus;
		}
		Optional<Long> dossier = DossierController.numero(dossierId);
		Optional<Long> facture = DossierController.numero(factureId);
		if (dossier.isEmpty() || facture.isEmpty()) {
			return factureIntrouvable();
		}
		return factures.consulter(session.compte(), dossier.get(), facture.get())
				.<ResponseEntity<Object>>map(ResponseEntity::ok).orElseGet(FactureController::factureIntrouvable);
	}

	// US-13 RG1 : aperçu (affiché dans la page) ; RG2 : téléchargement du fichier d'origine.
	@GetMapping("/{factureId}/fichier")
	public ResponseEntity<Object> fichier(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId, @PathVariable String factureId,
			@RequestParam(value = "telechargement", defaultValue = "false") boolean telechargement) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = DossierController.refus(session);
		if (refus != null) {
			return refus;
		}
		Optional<Long> dossier = DossierController.numero(dossierId);
		Optional<Long> facture = DossierController.numero(factureId);
		if (dossier.isEmpty() || facture.isEmpty()) {
			return factureIntrouvable();
		}
		return factures.ouvrir(session.compte(), dossier.get(), facture.get(), telechargement)
				.map(fichier -> envoyer(fichier, telechargement)).orElseGet(FactureController::factureIntrouvable);
	}

	private static ResponseEntity<Object> envoyer(FichierFacture fichier, boolean telechargement) {
		ContentDisposition disposition = (telechargement ? ContentDisposition.attachment() : ContentDisposition.inline())
				.filename(fichier.nomFichier(), StandardCharsets.UTF_8).build();
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(fichier.typeFichier()))
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				// Fichier privé : jamais gardé par un cache, jamais interprété autrement que son type.
				.header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Content-Type-Options", "nosniff")
				.body(fichier.contenu());
	}

	private static ResponseEntity<Object> factureIntrouvable() {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErreurResponse(MESSAGE_FACTURE_INTROUVABLE));
	}

	@PostMapping("/exemples")
	public ResponseEntity<Object> ajouterExemples(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = DossierController.refus(session);
		if (refus != null) {
			return refus;
		}
		Optional<Long> numero = DossierController.numero(dossierId);
		if (numero.isEmpty() || !factures.ajouterExemples(session.compte(), numero.get())) {
			return DossierController.introuvable();
		}
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	private static Optional<Facture.Sens> sens(String valeur) {
		for (Facture.Sens sens : Facture.Sens.values()) {
			if (sens.name().equals(valeur.trim().toUpperCase(Locale.ROOT))) {
				return Optional.of(sens);
			}
		}
		return Optional.empty();
	}

}
