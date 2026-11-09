package com.example.gestionfinanciere.facture;

import java.util.Locale;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

// US-12 Factures d'un dossier, exposées par le gateway sous /backend/dossiers/{id}/factures ;
// mêmes droits que le dossier (US-11 RG3), session obligatoire (US-07) :
//   GET  /dossiers/{id}/factures?sens=ACHAT|VENTE  factures fournisseurs (achats) ou clients (ventes)
//   POST /dossiers/{id}/factures/exemples          mode test seulement : factures d'exemple
@RestController
@RequestMapping("/dossiers/{dossierId}/factures")
public class FactureController {

	static final String MESSAGE_SENS_INVALIDE = "Indiquez ACHAT ou VENTE";

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
