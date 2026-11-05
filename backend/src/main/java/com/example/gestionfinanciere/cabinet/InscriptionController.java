package com.example.gestionfinanciere.cabinet;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.ErreurResponse;
import com.example.gestionfinanciere.compte.ResultatRenvoi;
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.compte.SessionService;
import com.example.gestionfinanciere.notification.EnvoiImpossibleException;

// US-36 Créer le compte de son cabinet, exposé par le gateway sous /backend/inscription/... :
//   POST /inscription                 formulaire « Vos informations » -> envoi du code SMS
//   POST /inscription/code            code SMS -> cabinet créé, administrateur connecté
//   POST /inscription/code/renvoyer   nouveau code après 60 s
@RestController
@RequestMapping("/inscription")
public class InscriptionController {

	static final String MESSAGE_FORMULAIRE = "Certains champs sont à corriger";
	static final String MESSAGE_SIREN_DEJA_UTILISE = "Un cabinet existe déjà avec ce SIREN. "
			+ "Demandez à son administrateur de vous inviter.";
	static final String MESSAGE_EMAIL_DEJA_UTILISE = "Un compte existe déjà avec cet email. "
			+ "Connectez-vous ou utilisez une autre adresse.";
	static final String MESSAGE_SMS_IMPOSSIBLE = "Le SMS n'a pas pu être envoyé. Vérifiez le numéro de mobile "
			+ "et réessayez.";
	static final String MESSAGE_DEMANDE_INVALIDE = "Votre demande n'est plus valable. Recommencez la création "
			+ "du compte.";
	static final String MESSAGE_TROP_DE_CODES = "Trop de codes incorrects. Vérifiez votre numéro de mobile "
			+ "et recommencez.";
	static final String MESSAGE_CODE_EXPIRE = "Ce code a expiré. Demandez un nouveau code.";

	// erreurs : nom du champ -> message, affiché sous le champ concerné.
	public record ErreursFormulaire(String message, Map<String, String> erreurs) {
	}

	public record CodeRequest(String defi, String code) {
	}

	public record RenvoiRequest(String defi) {
	}

	// Étape 3 « Cabinet prêt » : l'administrateur est connecté (même session que US-07).
	public record CabinetCree(String jeton, Role role, String nomAffiche, String nomCabinet) {
	}

	// retourFormulaire : l'écran doit revenir au formulaire « Vos informations ».
	public record ErreurCode(String message, int tentativesRestantes, boolean retourFormulaire) {
	}

	public record CodeRenvoye(String telephoneMasque, String codeDeTest) {
	}

	public record RenvoiTropTot(String message, long secondesRestantes) {
	}

	private final InscriptionService inscription;
	private final SessionService sessions;

	public InscriptionController(InscriptionService inscription, SessionService sessions) {
		this.inscription = inscription;
		this.sessions = sessions;
	}

	@PostMapping
	public ResponseEntity<Object> demarrer(@RequestBody DemandeInscription demande) {
		try {
			ResultatInscription resultat = inscription.demarrer(demande);
			return switch (resultat.statut()) {
				case CODE_ENVOYE -> ResponseEntity.ok(resultat.defi());
				case INVALIDE -> ResponseEntity.badRequest()
						.body(new ErreursFormulaire(MESSAGE_FORMULAIRE, resultat.erreurs()));
				case SIREN_DEJA_UTILISE -> conflit("siren", MESSAGE_SIREN_DEJA_UTILISE);
				case EMAIL_DEJA_UTILISE -> conflit("email", MESSAGE_EMAIL_DEJA_UTILISE);
			};
		} catch (EnvoiImpossibleException e) {
			return erreur(HttpStatus.SERVICE_UNAVAILABLE, MESSAGE_SMS_IMPOSSIBLE);
		}
	}

	@PostMapping("/code")
	public ResponseEntity<Object> validerCode(@RequestBody CodeRequest demande) {
		ResultatCodeInscription resultat = inscription.validerCode(demande.defi(), demande.code());
		return switch (resultat.statut()) {
			case CABINET_CREE -> ouvrirSession(resultat.compte());
			case FAUX -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new ErreurCode(messageCodeFaux(resultat.tentativesRestantes()),
							resultat.tentativesRestantes(), false));
			case TROP_DE_CODES_FAUX -> retourFormulaire(HttpStatus.UNAUTHORIZED, MESSAGE_TROP_DE_CODES);
			case EXPIRE -> ResponseEntity.status(HttpStatus.GONE)
					.body(new ErreurCode(MESSAGE_CODE_EXPIRE, resultat.tentativesRestantes(), false));
			case DEMANDE_INVALIDE -> retourFormulaire(HttpStatus.UNAUTHORIZED, MESSAGE_DEMANDE_INVALIDE);
			case SIREN_DEJA_UTILISE -> retourFormulaire(HttpStatus.CONFLICT, MESSAGE_SIREN_DEJA_UTILISE);
			case EMAIL_DEJA_UTILISE -> retourFormulaire(HttpStatus.CONFLICT, MESSAGE_EMAIL_DEJA_UTILISE);
		};
	}

	@PostMapping("/code/renvoyer")
	public ResponseEntity<Object> renvoyerCode(@RequestBody RenvoiRequest demande) {
		try {
			ResultatRenvoi resultat = inscription.renvoyer(demande.defi());
			return switch (resultat.statut()) {
				case RENVOYE -> ResponseEntity.ok(new CodeRenvoye(resultat.telephoneMasque(), resultat.codeDeTest()));
				case TROP_TOT -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
						.body(new RenvoiTropTot("Vous pourrez demander un nouveau code dans "
								+ resultat.secondesRestantes() + " s.", resultat.secondesRestantes()));
				case DEMANDE_INVALIDE -> retourFormulaire(HttpStatus.UNAUTHORIZED, MESSAGE_DEMANDE_INVALIDE);
			};
		} catch (EnvoiImpossibleException e) {
			return erreur(HttpStatus.SERVICE_UNAVAILABLE, MESSAGE_SMS_IMPOSSIBLE);
		}
	}

	private ResponseEntity<Object> ouvrirSession(Compte administrateur) {
		String jeton = sessions.ouvrir(administrateur);
		return ResponseEntity.ok(new CabinetCree(jeton, administrateur.getRole(), administrateur.getNomAffiche(),
				administrateur.getCabinet().getNom()));
	}

	private static ResponseEntity<Object> conflit(String champ, String message) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErreursFormulaire(message, Map.of(champ, message)));
	}

	private static ResponseEntity<Object> retourFormulaire(HttpStatus statut, String message) {
		return ResponseEntity.status(statut).body(new ErreurCode(message, 0, true));
	}

	private static ResponseEntity<Object> erreur(HttpStatus statut, String message) {
		return ResponseEntity.status(statut).body(new ErreurResponse(message));
	}

	// « Code incorrect. Il vous reste 2 tentatives. » (même message que US-04 CA2)
	static String messageCodeFaux(int restantes) {
		return "Code incorrect. Il vous reste " + restantes + (restantes > 1 ? " tentatives." : " tentative.");
	}

}
