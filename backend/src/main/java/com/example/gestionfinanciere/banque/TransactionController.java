package com.example.gestionfinanciere.banque;

import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.SessionController;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.dossier.DossierController;

// US-58 Transactions bancaires d'un dossier (bouchon ou, plus tard, connexion bancaire), exposées
// par le gateway sous /backend/dossiers/{id}/transactions ; lues par l'onglet Banque (US-45).
// Mêmes droits que le dossier (US-11 RG3, US-20), session obligatoire (US-07) :
//   GET  /dossiers/{id}/transactions                         transactions du dossier, les plus récentes
//                                                            d'abord, avec leur validation (US-30)
//   POST /dossiers/{id}/transactions/{reference}/validation  valider l'opération et générer son écriture
//                                                            (US-30 RG1) ; corps {"categorie","numeroCompte","tauxTva"}
// Réponse de la validation : la transaction à jour, ou {"message"} (400, 404, 409).
@RestController
@RequestMapping("/dossiers/{dossierId}/transactions")
public class TransactionController {

	private final TransactionService transactions;
	private final ValidationOperationService validations;
	private final SessionService sessions;

	public TransactionController(TransactionService transactions, ValidationOperationService validations,
			SessionService sessions) {
		this.transactions = transactions;
		this.validations = validations;
		this.sessions = sessions;
	}

	@GetMapping
	public ResponseEntity<Object> lister(
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
		return transactions.lister(session.compte(), numero.get())
				.<ResponseEntity<Object>>map(ResponseEntity::ok).orElseGet(DossierController::introuvable);
	}

	@PostMapping("/{reference}/validation")
	public ResponseEntity<Object> valider(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId, @PathVariable String reference,
			@RequestBody(required = false) DemandeValidation demande) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = DossierController.refus(session);
		if (refus != null) {
			return refus;
		}
		Optional<Long> numero = DossierController.numero(dossierId);
		if (numero.isEmpty()) {
			return DossierController.introuvable();
		}
		ResultatValidation resultat = validations.valider(session.compte(), numero.get(), reference, demande);
		return switch (resultat.issue()) {
			case VALIDEE -> ResponseEntity.ok(resultat.transaction());
			case INTROUVABLE -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErreurResponse(resultat.message()));
			case INVALIDE -> ResponseEntity.badRequest().body(new ErreurResponse(resultat.message()));
			case DEJA_VALIDEE -> ResponseEntity.status(HttpStatus.CONFLICT).body(new ErreurResponse(resultat.message()));
		};
	}

}
