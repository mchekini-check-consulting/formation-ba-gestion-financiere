package com.example.gestionfinanciere.dossier;

import java.util.Map;

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

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.compte.SessionController;
import com.example.gestionfinanciere.compte.SessionController.ErreurSession;
import com.example.gestionfinanciere.compte.SessionService;

// US-10 Dossiers clients, exposés par le gateway sous /backend/dossiers/... ; avec l'en-tête
// "Authorization: Bearer <jeton>" de la session (US-07), réservés aux collaborateurs d'un cabinet :
//   POST /dossiers       créer un dossier (raison sociale, SIREN)
//   GET  /dossiers/{id}  ouvrir un dossier du cabinet
@RestController
@RequestMapping("/dossiers")
public class DossierController {

	static final String MESSAGE_RESERVE = "Seuls les collaborateurs d'un cabinet ont accès aux dossiers";
	static final String MESSAGE_FORMULAIRE = "Certains champs sont à corriger";
	static final String MESSAGE_SIREN_DEJA_UTILISE = "Un dossier existe déjà pour ce SIREN dans ce cabinet";
	static final String MESSAGE_INTROUVABLE = "Dossier introuvable";

	public record ErreursFormulaire(String message, Map<String, String> erreurs) {
	}

	private final DossierService dossiers;
	private final SessionService sessions;

	public DossierController(DossierService dossiers, SessionService sessions) {
		this.dossiers = dossiers;
		this.sessions = sessions;
	}

	@PostMapping
	public ResponseEntity<Object> creer(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@RequestBody DemandeDossier demande) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = refus(session);
		if (refus != null) {
			return refus;
		}
		ResultatCreationDossier resultat = dossiers.creer(session.compte(), demande);
		return switch (resultat.statut()) {
			case CREE -> ResponseEntity.status(HttpStatus.CREATED).body(resultat.dossier());
			case INVALIDE -> ResponseEntity.badRequest()
					.body(new ErreursFormulaire(MESSAGE_FORMULAIRE, resultat.erreurs()));
			case SIREN_DEJA_UTILISE -> ResponseEntity.status(HttpStatus.CONFLICT).body(new ErreursFormulaire(
					MESSAGE_SIREN_DEJA_UTILISE, Map.of("siren", MESSAGE_SIREN_DEJA_UTILISE)));
		};
	}

	@GetMapping("/{id}")
	public ResponseEntity<Object> consulter(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable Long id) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = refus(session);
		if (refus != null) {
			return refus;
		}
		return dossiers.consulter(session.compte(), id).<ResponseEntity<Object>>map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(new ErreurResponse(MESSAGE_INTROUVABLE)));
	}

	// US-07 : session absente ou expirée -> 401 ; pas collaborateur d'un cabinet -> 403.
	private static ResponseEntity<Object> refus(ResultatSession session) {
		return switch (session.statut()) {
			case NON_CONNECTE -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new ErreurSession(SessionController.MESSAGE_NON_CONNECTE, "NON_CONNECTE"));
			case EXPIREE -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new ErreurSession(SessionController.MESSAGE_SESSION_EXPIREE, "SESSION_EXPIREE"));
			case ACTIVE -> estCollaborateurDUnCabinet(session.compte()) ? null
					: ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErreurResponse(MESSAGE_RESERVE));
		};
	}

	private static boolean estCollaborateurDUnCabinet(Compte compte) {
		return compte.getRole() == Role.COLLABORATEUR && compte.getCabinet() != null;
	}

}
