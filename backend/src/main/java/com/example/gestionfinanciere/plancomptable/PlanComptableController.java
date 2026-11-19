package com.example.gestionfinanciere.plancomptable;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import com.example.gestionfinanciere.dossier.DossierController.ErreursFormulaire;

// US-41 Plan comptable d'un dossier, exposé par le gateway sous /backend/dossiers/{id}/plan-comptable ;
// lu par l'onglet « Plan comptable ». Mêmes droits que le dossier (US-11 RG3, US-20), session
// obligatoire (US-07) :
//   GET    /dossiers/{id}/plan-comptable           comptes du dossier et comptes par défaut (RG2)
//   POST   /dossiers/{id}/plan-comptable           nouveau sous-compte {numero, libelle} (RG1)
//   PATCH  /dossiers/{id}/plan-comptable/{numero}  désactiver / réactiver {actif} (RG3)
//   DELETE /dossiers/{id}/plan-comptable/{numero}  supprimer un sous-compte (RG3)
@RestController
@RequestMapping("/dossiers/{dossierId}/plan-comptable")
public class PlanComptableController {

	static final String MESSAGE_FORMULAIRE = "Certains champs sont à corriger";
	static final String MESSAGE_DEJA_EXISTANT = "Ce numéro de compte existe déjà dans le plan";
	static final String MESSAGE_COMPTE_INTROUVABLE = "Compte introuvable dans le plan du dossier";
	static final String MESSAGE_COMPTE_GENERAL =
			"Un compte du plan général ne peut pas être supprimé : désactivez-le";

	private final PlanComptableService plans;
	private final SessionService sessions;

	public PlanComptableController(PlanComptableService plans, SessionService sessions) {
		this.plans = plans;
		this.sessions = sessions;
	}

	@GetMapping
	public ResponseEntity<Object> consulter(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId) {
		return avecDossier(autorisation, dossierId, (compte, numero) -> plans.consulter(compte, numero)
				.<ResponseEntity<Object>>map(ResponseEntity::ok).orElseGet(DossierController::introuvable));
	}

	@PostMapping
	public ResponseEntity<Object> creer(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId, @RequestBody DemandeCompte demande) {
		return avecDossier(autorisation, dossierId,
				(compte, numero) -> reponse(plans.creer(compte, numero, demande)));
	}

	@PatchMapping("/{numeroCompte}")
	public ResponseEntity<Object> changerActivation(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId, @PathVariable String numeroCompte,
			@RequestBody DemandeActivation demande) {
		return avecDossier(autorisation, dossierId,
				(compte, numero) -> reponse(plans.changerActivation(compte, numero, numeroCompte, demande)));
	}

	@DeleteMapping("/{numeroCompte}")
	public ResponseEntity<Object> supprimer(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String dossierId, @PathVariable String numeroCompte) {
		return avecDossier(autorisation, dossierId,
				(compte, numero) -> reponse(plans.supprimer(compte, numero, numeroCompte)));
	}

	private interface Action {
		ResponseEntity<Object> executer(Compte collaborateur, Long dossierId);
	}

	// US-07 : session ; US-11 RG3 : numéro de dossier lu dans l'adresse.
	private ResponseEntity<Object> avecDossier(String autorisation, String dossierId, Action action) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = DossierController.refus(session);
		if (refus != null) {
			return refus;
		}
		Optional<Long> numero = DossierController.numero(dossierId);
		if (numero.isEmpty()) {
			return DossierController.introuvable();
		}
		return action.executer(session.compte(), numero.get());
	}

	static ResponseEntity<Object> reponse(ResultatCompte resultat) {
		Function<String, ResponseEntity<Object>> conflit = message -> ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErreurResponse(message));
		return switch (resultat.statut()) {
			case CREE -> ResponseEntity.status(HttpStatus.CREATED).body(resultat.compte());
			case MODIFIE -> ResponseEntity.ok(resultat.compte());
			case SUPPRIME -> ResponseEntity.noContent().build();
			case INVALIDE -> ResponseEntity.badRequest()
					.body(new ErreursFormulaire(MESSAGE_FORMULAIRE, resultat.erreurs()));
			case DEJA_EXISTANT -> ResponseEntity.status(HttpStatus.CONFLICT).body(
					new ErreursFormulaire(MESSAGE_DEJA_EXISTANT, Map.of("numero", MESSAGE_DEJA_EXISTANT)));
			case COMPTE_GENERAL -> conflit.apply(MESSAGE_COMPTE_GENERAL);
			case COMPTE_INTROUVABLE -> ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(new ErreurResponse(MESSAGE_COMPTE_INTROUVABLE));
			case DOSSIER_INTROUVABLE -> DossierController.introuvable();
		};
	}

}
