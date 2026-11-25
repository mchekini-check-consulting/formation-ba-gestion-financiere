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

// US-55 Fiche facture, exposée par le gateway sous /backend/dossiers/{id}/factures/{f} ; mêmes droits
// que le dossier (US-11 RG3), session obligatoire (US-07), collaborateur du cabinet seulement :
//   PUT  /dossiers/{id}/factures/{f}/informations  enregistrer les champs corrigés (RG2, RG3, RG7, RG8)
//   POST /dossiers/{id}/factures/{f}/validation    enregistrer puis valider (RG3 à RG6)
// Corps : {typeTiers, nomTiers, numeroFacture, dateEmission, montantHt, montantTva, tauxTva, montantTtc,
// compteContrepartie}. Réponse : {facture, suivante} ; refus : 400 avec le message, 409 si la facture
// est traitée ou en cours de lecture (RG8).
@RestController
@RequestMapping("/dossiers/{dossierId}/factures/{factureId}")
public class FicheFactureController {

	static final String MESSAGE_NON_MODIFIABLE = "Cette facture n'est plus modifiable : elle est déjà traitée ou en cours de lecture";
	static final String MESSAGE_INFORMATIONS_MANQUANTES = "Indiquez les informations de la facture";

	// Réponse d'un enregistrement ou d'une validation réussi : fiche à jour et facture suivante à traiter.
	public record ReponseFiche(VueFacture facture, Long suivante) {
	}

	// Une action de la fiche : enregistrer ou valider.
	private interface Action {
		ResultatFiche appliquer(Compte collaborateur, Long dossierId, Long factureId, InformationsFacture demande);
	}

	private final FicheFactureService fiches;
	private final SessionService sessions;

	public FicheFactureController(FicheFactureService fiches, SessionService sessions) {
		this.fiches = fiches;
		this.sessions = sessions;
	}

	@PutMapping("/informations")
	public ResponseEntity<Object> enregistrer(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId, @PathVariable String factureId,
			@RequestBody(required = false) InformationsFacture demande) {
		return traiter(autorisation, dossierId, factureId, demande, fiches::enregistrer);
	}

	@PostMapping("/validation")
	public ResponseEntity<Object> valider(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId, @PathVariable String factureId,
			@RequestBody(required = false) InformationsFacture demande) {
		return traiter(autorisation, dossierId, factureId, demande, fiches::valider);
	}

	private ResponseEntity<Object> traiter(String autorisation, String dossierId, String factureId,
			InformationsFacture demande, Action action) {
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
		if (demande == null) {
			return ResponseEntity.badRequest().body(new ErreurResponse(MESSAGE_INFORMATIONS_MANQUANTES));
		}
		return reponse(action.appliquer(session.compte(), dossier.get(), facture.get(), demande));
	}

	private static ResponseEntity<Object> reponse(ResultatFiche resultat) {
		return switch (resultat.issue()) {
			case ENREGISTREE, VALIDEE -> ResponseEntity.ok(new ReponseFiche(resultat.facture(), resultat.suivante()));
			case INTROUVABLE -> introuvable();
			case NON_MODIFIABLE -> ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new ErreurResponse(MESSAGE_NON_MODIFIABLE));
			case REFUSEE -> ResponseEntity.badRequest().body(new ErreurResponse(resultat.message()));
		};
	}

	private static ResponseEntity<Object> introuvable() {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErreurResponse(FactureController.MESSAGE_FACTURE_INTROUVABLE));
	}

}
