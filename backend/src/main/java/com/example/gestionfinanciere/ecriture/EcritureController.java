package com.example.gestionfinanciere.ecriture;

import java.util.Optional;
import java.util.function.BiFunction;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.SessionController;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.dossier.DossierController;

// US-47 Journal des écritures d'un dossier, exposé par le gateway sous /backend/dossiers/{id}/ecritures ;
// lu par l'onglet « Écritures ». Mêmes droits que le dossier (US-11 RG3, US-20), session obligatoire (US-07) :
//   GET /dossiers/{id}/ecritures?periode=2026-06   écritures de la période (RG1), le dernier mois par défaut
//   PUT /dossiers/{id}/ecritures/{ecritureId}      correction {libelle, lignes[{numeroCompte, debit, credit}]}
@RestController
@RequestMapping("/dossiers/{dossierId}/ecritures")
public class EcritureController {

	static final String MESSAGE_ECRITURE_INTROUVABLE = "Écriture introuvable dans ce dossier";

	private final JournalEcrituresService journal;
	private final SessionService sessions;

	public EcritureController(JournalEcrituresService journal, SessionService sessions) {
		this.journal = journal;
		this.sessions = sessions;
	}

	@GetMapping
	public ResponseEntity<Object> consulter(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId, @RequestParam(required = false) String periode) {
		return avecDossier(autorisation, dossierId, (compte, numero) -> journal.consulter(compte, numero, periode)
				.<ResponseEntity<Object>>map(ResponseEntity::ok).orElseGet(DossierController::introuvable));
	}

	@PutMapping("/{ecritureId}")
	public ResponseEntity<Object> modifier(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId, @PathVariable String ecritureId,
			@RequestBody(required = false) CorrectionEcriture correction) {
		return avecDossier(autorisation, dossierId, (compte, numero) -> {
			Optional<Long> ecriture = DossierController.numero(ecritureId);
			if (ecriture.isEmpty()) {
				return introuvable();
			}
			return reponse(journal.modifier(compte, numero, ecriture.get(), correction));
		});
	}

	// US-07 : session ; US-11 RG3 : numéro de dossier lu dans l'adresse.
	private ResponseEntity<Object> avecDossier(String autorisation, String dossierId,
			BiFunction<Compte, Long, ResponseEntity<Object>> action) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = DossierController.refus(session);
		if (refus != null) {
			return refus;
		}
		Optional<Long> numero = DossierController.numero(dossierId);
		if (numero.isEmpty()) {
			return DossierController.introuvable();
		}
		return action.apply(session.compte(), numero.get());
	}

	static ResponseEntity<Object> reponse(ResultatEcriture resultat) {
		return switch (resultat.statut()) {
			case MODIFIEE -> ResponseEntity.ok(resultat.ecriture());
			case INVALIDE -> ResponseEntity.badRequest().body(new ErreurResponse(resultat.message()));
			case EXPORTEE -> ResponseEntity.status(HttpStatus.CONFLICT).body(new ErreurResponse(resultat.message()));
			case ECRITURE_INTROUVABLE -> introuvable();
			case DOSSIER_INTROUVABLE -> DossierController.introuvable();
		};
	}

	private static ResponseEntity<Object> introuvable() {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErreurResponse(MESSAGE_ECRITURE_INTROUVABLE));
	}

}
