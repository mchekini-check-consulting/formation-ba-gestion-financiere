package com.example.gestionfinanciere.facture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatSession;
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.compte.SessionController;
import com.example.gestionfinanciere.compte.SessionController.ErreurSession;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.dossier.DossierController;
import com.example.gestionfinanciere.logging.EvenementMetier;

// Espace client du dirigeant, exposé par le gateway sous /backend/espace-client/factures ; avec
// l'en-tête "Authorization: Bearer <jeton>" de la session (US-07), réservé au dirigeant d'un dossier :
//   GET    /espace-client/factures?sens=ACHAT|VENTE  ses dépôts de l'onglet (US-16 RG1)
//   POST   /espace-client/factures?sens=ACHAT|VENTE  déposer un fichier, champ « fichier » (US-15, US-38)
//   DELETE /espace-client/factures/{id}              supprimer un dépôt pas encore ouvert (US-16)
@RestController
@RequestMapping("/espace-client/factures")
public class EspaceClientController {

	static final String MESSAGE_RESERVE = "Seuls les dirigeants clients ont accès à leur espace";
	static final String MESSAGE_SENS_INVALIDE = "Indiquez ACHAT ou VENTE";
	// US-15 CA2.
	public static final String MESSAGE_FORMAT = "Format non accepté : PDF, JPG ou PNG uniquement";
	public static final String MESSAGE_TAILLE = "Fichier trop volumineux : 10 Mo maximum";
	static final String MESSAGE_INTROUVABLE = "Facture introuvable ou accès non autorisé";
	// US-16 RG3.
	static final String MESSAGE_DEJA_OUVERTE = "Votre cabinet a déjà ouvert cette facture : elle ne peut plus être supprimée";

	private final DepotFactureService depots;
	private final SessionService sessions;

	public EspaceClientController(DepotFactureService depots, SessionService sessions) {
		this.depots = depots;
		this.sessions = sessions;
	}

	@GetMapping
	public ResponseEntity<Object> lister(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@RequestParam(value = "sens", defaultValue = "ACHAT") String sens) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = refus(session);
		if (refus != null) {
			return refus;
		}
		Optional<Facture.Sens> onglet = sens(sens);
		if (onglet.isEmpty()) {
			return ResponseEntity.badRequest().body(new ErreurResponse(MESSAGE_SENS_INVALIDE));
		}
		return ResponseEntity.ok(depots.lister(session.compte(), onglet.get()));
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Object> deposer(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@RequestParam(value = "sens", defaultValue = "ACHAT") String sens,
			@RequestPart(value = "fichier", required = false) MultipartFile fichier) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = refus(session);
		if (refus != null) {
			return refus;
		}
		Optional<Facture.Sens> onglet = sens(sens);
		if (onglet.isEmpty()) {
			return ResponseEntity.badRequest().body(new ErreurResponse(MESSAGE_SENS_INVALIDE));
		}
		if (fichier == null) {
			return ResponseEntity.badRequest().body(new ErreurResponse(MESSAGE_FORMAT));
		}
		if (fichier.getSize() > DepotFactureService.TAILLE_MAX) {
			return ResponseEntity.badRequest().body(new ErreurResponse(MESSAGE_TAILLE));
		}
		ResultatDepot resultat = depots.deposer(session.compte(), onglet.get(), fichier.getOriginalFilename(),
				contenu(fichier));
		return switch (resultat.statut()) {
			case DEPOSEE -> ResponseEntity.status(HttpStatus.CREATED).body(resultat.facture());
			case FORMAT_REFUSE -> ResponseEntity.badRequest().body(new ErreurResponse(MESSAGE_FORMAT));
			case TROP_VOLUMINEUX -> ResponseEntity.badRequest().body(new ErreurResponse(MESSAGE_TAILLE));
		};
	}

	@DeleteMapping("/{factureId}")
	public ResponseEntity<Object> supprimer(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorisation,
			@PathVariable String factureId) {
		ResultatSession session = sessions.consulter(SessionController.jeton(autorisation));
		ResponseEntity<Object> refus = refus(session);
		if (refus != null) {
			return refus;
		}
		Optional<Long> numero = DossierController.numero(factureId);
		if (numero.isEmpty()) {
			return introuvable();
		}
		return switch (depots.supprimer(session.compte(), numero.get())) {
			case SUPPRIMEE -> ResponseEntity.noContent().build();
			case INTROUVABLE -> introuvable();
			case DEJA_OUVERTE -> ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new ErreurResponse(MESSAGE_DEJA_OUVERTE));
		};
	}

	private static byte[] contenu(MultipartFile fichier) {
		try {
			return fichier.getBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static ResponseEntity<Object> introuvable() {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErreurResponse(MESSAGE_INTROUVABLE));
	}

	// US-07 : session absente ou expirée -> 401 ; pas dirigeant actif d'un dossier -> 403 ; sinon null.
	static ResponseEntity<Object> refus(ResultatSession session) {
		return switch (session.statut()) {
			case NON_CONNECTE -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new ErreurSession(SessionController.MESSAGE_NON_CONNECTE, "NON_CONNECTE"));
			case EXPIREE -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new ErreurSession(SessionController.MESSAGE_SESSION_EXPIREE, "SESSION_EXPIREE"));
			case ACTIVE -> estDirigeant(session.compte()) ? null : interdit(session.compte());
		};
	}

	private static ResponseEntity<Object> interdit(Compte compte) {
		EvenementMetier.de("espace_client.acces", "refuse").depuisLeControleur().avec(compte.identifiantsPourLogs())
				.warn();
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErreurResponse(MESSAGE_RESERVE));
	}

	private static boolean estDirigeant(Compte compte) {
		return compte.getRole() == Role.DIRIGEANT && compte.getDossierId() != null && compte.isActif();
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
