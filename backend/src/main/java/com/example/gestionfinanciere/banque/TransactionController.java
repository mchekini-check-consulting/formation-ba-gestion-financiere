package com.example.gestionfinanciere.banque;

import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.SessionController;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.dossier.DossierController;

// US-58 Transactions bancaires d'un dossier (bouchon ou, plus tard, connexion bancaire), exposées
// par le gateway sous /backend/dossiers/{id}/transactions ; lues par l'onglet Banque (US-45).
// Mêmes droits que le dossier (US-11 RG3, US-20), session obligatoire (US-07) :
//   GET /dossiers/{id}/transactions  transactions du dossier, les plus récentes d'abord
@RestController
@RequestMapping("/dossiers/{dossierId}/transactions")
public class TransactionController {

	private final TransactionService transactions;
	private final SessionService sessions;

	public TransactionController(TransactionService transactions, SessionService sessions) {
		this.transactions = transactions;
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

}
