package com.example.gestionfinanciere.dossier;

import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.SessionController;
import com.example.gestionfinanciere.compte.SessionService;

// US-20 Comptables d'un dossier, exposés par le gateway sous /backend/dossiers/{id}/comptables ;
// mêmes droits que le dossier (RG2), session obligatoire (US-07) :
//   GET /dossiers/{id}/comptables  comptables du cabinet, cochés s'ils suivent le dossier
//   PUT /dossiers/{id}/comptables  {"comptableIds":[...]} : RG1, administrateur ou associé seulement
@RestController
@RequestMapping("/dossiers/{dossierId}/comptables")
public class AttributionController {

	static final String MESSAGE_INTERDIT = "Seuls l'administrateur et les associés attribuent les dossiers";
	static final String MESSAGE_INVALIDE = "Choisissez des comptables du cabinet";

	private final AttributionService attributions;
	private final SessionService sessions;

	public AttributionController(AttributionService attributions, SessionService sessions) {
		this.attributions = attributions;
		this.sessions = sessions;
	}

	@GetMapping
	public ResponseEntity<Object> consulter(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = DossierController.refus(session);
		if (refus != null) {
			return refus;
		}
		Optional<Long> numero = DossierController.numero(dossierId);
		if (numero.isEmpty()) {
			return DossierController.introuvable();
		}
		return attributions.consulter(session.compte(), numero.get()).<ResponseEntity<Object>>map(ResponseEntity::ok)
				.orElseGet(DossierController::introuvable);
	}

	@PutMapping
	public ResponseEntity<Object> attribuer(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId, @RequestBody(required = false) DemandeAttribution demande) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = DossierController.refus(session);
		if (refus != null) {
			return refus;
		}
		Optional<Long> numero = DossierController.numero(dossierId);
		if (numero.isEmpty()) {
			return DossierController.introuvable();
		}
		ResultatAttribution resultat = attributions.attribuer(session.compte(), numero.get(), demande);
		return switch (resultat.statut()) {
			case ATTRIBUE -> ResponseEntity.ok(resultat.comptables());
			case INTERDIT -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErreurResponse(MESSAGE_INTERDIT));
			case INTROUVABLE -> DossierController.introuvable();
			case INVALIDE -> ResponseEntity.badRequest().body(new ErreurResponse(MESSAGE_INVALIDE));
		};
	}

}
