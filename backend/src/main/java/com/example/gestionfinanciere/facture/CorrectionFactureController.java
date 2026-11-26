package com.example.gestionfinanciere.facture;

import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.SessionController;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.dossier.DossierController;

// US-55 Vérifier, corriger et valider les informations d'une facture, exposé par le gateway sous
// /backend/dossiers/{id}/factures ; mêmes droits que le dossier (US-11 RG3), session obligatoire (US-07) :
//   PUT  /dossiers/{id}/factures/{f}/informations  enregistrer les champs corrigés ou saisis (RG2, RG3, RG7)
//   POST /dossiers/{id}/factures/{f}/validation    enregistrer puis passer en « Traitée » (RG4 à RG6) ;
//                                                  facture d'achat : génère son écriture (US-56 RG1)
//   POST /dossiers/{id}/factures/{f}/ecriture      « Re-générer les écritures », sans corps (US-56 RG5)
// Corps : {"typeTiers","nomTiers","numeroFacture","dateEmission","montantHt","montantTva","tauxTva",
// "montantTtc","compteContrepartie"} ; réponse : la fiche à jour, ou {"message"} (400, 404, 409).
@RestController
@RequestMapping("/dossiers/{dossierId}/factures/{factureId}")
public class CorrectionFactureController {

	private final CorrectionFactureService corrections;
	private final SessionService sessions;

	public CorrectionFactureController(CorrectionFactureService corrections, SessionService sessions) {
		this.corrections = corrections;
		this.sessions = sessions;
	}

	@FunctionalInterface
	private interface Traitement {
		ResultatCorrection appliquer(Compte collaborateur, Long dossierId, Long factureId,
				InformationsFacture informations);
	}

	@PutMapping("/informations")
	public ResponseEntity<Object> enregistrer(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId, @PathVariable String factureId,
			@RequestBody(required = false) InformationsFacture informations) {
		return traiter(autorisation, dossierId, factureId, informations, corrections::enregistrer);
	}

	@PostMapping("/validation")
	public ResponseEntity<Object> valider(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId, @PathVariable String factureId,
			@RequestBody(required = false) InformationsFacture informations) {
		return traiter(autorisation, dossierId, factureId, informations, corrections::valider);
	}

	@PostMapping("/ecriture")
	public ResponseEntity<Object> regenerer(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId, @PathVariable String factureId) {
		return traiter(autorisation, dossierId, factureId, null,
				(collaborateur, dossier, facture, informations) -> corrections.regenerer(collaborateur, dossier,
						facture));
	}

	private ResponseEntity<Object> traiter(String autorisation, String dossierId, String factureId,
			InformationsFacture informations, Traitement traitement) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = DossierController.refus(session);
		if (refus != null) {
			return refus;
		}
		Optional<Long> dossier = DossierController.numero(dossierId);
		Optional<Long> facture = DossierController.numero(factureId);
		if (dossier.isEmpty() || facture.isEmpty()) {
			return introuvable();
		}
		ResultatCorrection resultat = traitement.appliquer(session.compte(), dossier.get(), facture.get(),
				informations);
		return switch (resultat.issue()) {
			case ENREGISTREE, VALIDEE, REGENEREE -> ResponseEntity.ok(resultat.facture());
			case INTROUVABLE -> introuvable();
			case INVALIDE -> ResponseEntity.badRequest().body(new ErreurResponse(resultat.message()));
			case FIGEE -> ResponseEntity.status(HttpStatus.CONFLICT).body(new ErreurResponse(resultat.message()));
		};
	}

	private static ResponseEntity<Object> introuvable() {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErreurResponse(FactureController.MESSAGE_FACTURE_INTROUVABLE));
	}

}
