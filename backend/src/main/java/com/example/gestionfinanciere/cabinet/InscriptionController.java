package com.example.gestionfinanciere.cabinet;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.Role;
import com.example.gestionfinanciere.compte.SessionService;

// US-36 Créer le compte de son cabinet, exposé par le gateway sous /backend/inscription :
//   POST /inscription   formulaire « Vos informations » -> cabinet créé, administrateur connecté
// Le code de confirmation reçu par email a été retiré.
@RestController
@RequestMapping("/inscription")
public class InscriptionController {

	static final String MESSAGE_FORMULAIRE = "Certains champs sont à corriger";
	static final String MESSAGE_SIREN_DEJA_UTILISE = "Un cabinet existe déjà avec ce SIREN. "
			+ "Demandez à son administrateur de vous inviter.";
	static final String MESSAGE_EMAIL_DEJA_UTILISE = "Un compte existe déjà avec cet email. "
			+ "Connectez-vous ou utilisez une autre adresse.";

	// erreurs : nom du champ -> message, affiché sous le champ concerné.
	public record ErreursFormulaire(String message, Map<String, String> erreurs) {
	}

	// Étape 2 « Cabinet prêt » : l'administrateur est connecté (même session que US-07).
	public record CabinetCree(String jeton, Role role, String nomAffiche, String nomCabinet) {
	}

	private final InscriptionService inscription;
	private final SessionService sessions;

	public InscriptionController(InscriptionService inscription, SessionService sessions) {
		this.inscription = inscription;
		this.sessions = sessions;
	}

	@PostMapping
	public ResponseEntity<Object> creer(@RequestBody DemandeInscription demande) {
		ResultatInscription resultat = inscription.creer(demande);
		return switch (resultat.statut()) {
			case CABINET_CREE -> ouvrirSession(resultat.compte());
			case INVALIDE -> ResponseEntity.badRequest()
					.body(new ErreursFormulaire(MESSAGE_FORMULAIRE, resultat.erreurs()));
			case SIREN_DEJA_UTILISE -> conflit("siren", MESSAGE_SIREN_DEJA_UTILISE);
			case EMAIL_DEJA_UTILISE -> conflit("email", MESSAGE_EMAIL_DEJA_UTILISE);
		};
	}

	private ResponseEntity<Object> ouvrirSession(Compte administrateur) {
		String jeton = sessions.ouvrir(administrateur);
		return ResponseEntity.ok(new CabinetCree(jeton, administrateur.getRole(), administrateur.getNomAffiche(),
				administrateur.getCabinet().getNom()));
	}

	private static ResponseEntity<Object> conflit(String champ, String message) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErreursFormulaire(message, Map.of(champ, message)));
	}

}
