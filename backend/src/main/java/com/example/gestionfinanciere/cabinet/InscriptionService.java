package com.example.gestionfinanciere.cabinet;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.cabinet.ResultatCodeInscription.Statut;
import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.compte.CompteRepository;
import com.example.gestionfinanciere.compte.DefiCode;
import com.example.gestionfinanciere.compte.Jetons;
import com.example.gestionfinanciere.compte.ResultatRenvoi;
import com.example.gestionfinanciere.logging.Loggable;
import com.example.gestionfinanciere.notification.EnvoiEmail;

// US-36 Créer le compte de son cabinet, en 3 étapes (RG3) : informations, code reçu par email
// pour confirmer l'email, puis « Cabinet prêt ». Le code suit les règles de la US-04 :
// 6 chiffres, 5 minutes, 3 essais, nouveau code possible après 60 secondes.
@Service
@Loggable
public class InscriptionService {

	static final Duration VALIDITE_CODE = Duration.ofMinutes(5);
	static final Duration DELAI_AVANT_RENVOI = Duration.ofSeconds(60);
	static final int CODES_FAUX_MAX = 3;
	static final String SUJET = "Confirmez votre email Pointéo";

	private final InscriptionRepository inscriptions;
	private final CabinetRepository cabinets;
	private final CompteRepository comptes;
	private final PasswordEncoder encodeur;
	private final Clock horloge;
	private final EnvoiEmail envoiEmail;
	private final boolean modeTest;

	public InscriptionService(InscriptionRepository inscriptions, CabinetRepository cabinets,
			CompteRepository comptes, PasswordEncoder encodeur, Clock horloge, EnvoiEmail envoiEmail,
			@Value("${pointeo.mode-test:false}") boolean modeTest) {
		this.inscriptions = inscriptions;
		this.cabinets = cabinets;
		this.comptes = comptes;
		this.encodeur = encodeur;
		this.horloge = horloge;
		this.envoiEmail = envoiEmail;
		this.modeTest = modeTest;
	}

	// Étape 1 : vérifie le formulaire (RG2) puis envoie le code à l'email saisi.
	@Transactional
	public ResultatInscription demarrer(DemandeInscription demande) {
		Map<String, String> erreurs = ValidationInscription.erreurs(demande);
		if (!erreurs.isEmpty()) {
			return ResultatInscription.invalide(erreurs);
		}
		FormulaireValide formulaire = ValidationInscription.remettreEnForme(demande);
		if (cabinets.existsBySiren(formulaire.siren())) {
			return ResultatInscription.sirenDejaUtilise(); // CA2
		}
		if (comptes.findByEmail(formulaire.email()).isPresent()) {
			return ResultatInscription.emailDejaUtilise();
		}
		String defi = Jetons.nouveau();
		String code = Jetons.codeSixChiffres();
		Instant maintenant = horloge.instant();
		inscriptions.save(new Inscription(formulaire, encodeur.encode(formulaire.motDePasse()), maintenant,
				Jetons.empreinte(defi), encodeur.encode(code), maintenant, maintenant.plus(VALIDITE_CODE)));
		envoyer(formulaire.email(), code);
		return ResultatInscription.codeEnvoye(new DefiCode(defi, Compte.masquerEmail(formulaire.email()),
				codeDeTest(code)));
	}

	// Étape 2 : avec le bon code, crée le cabinet et son administrateur (CA1).
	@Transactional
	public ResultatCodeInscription validerCode(String defi, String code) {
		Optional<Inscription> trouvee = trouver(defi);
		if (trouvee.isEmpty()) {
			return ResultatCodeInscription.autre(Statut.DEMANDE_INVALIDE, 0);
		}
		Inscription inscription = trouvee.get();
		Instant maintenant = horloge.instant();
		if (inscription.estExpiree(maintenant)) {
			return ResultatCodeInscription.autre(Statut.EXPIRE, CODES_FAUX_MAX - inscription.getCodesFaux());
		}
		if (!estUnCode(code) || !encodeur.matches(code, inscription.getEmpreinteCode())) {
			int restantes = CODES_FAUX_MAX - inscription.enregistrerCodeFaux();
			if (restantes <= 0) {
				inscription.terminer();
				return ResultatCodeInscription.autre(Statut.TROP_DE_CODES_FAUX, 0);
			}
			return ResultatCodeInscription.faux(restantes);
		}
		inscription.terminer(); // le code ne sert qu'une fois
		if (cabinets.existsBySiren(inscription.getSiren())) {
			return ResultatCodeInscription.autre(Statut.SIREN_DEJA_UTILISE, 0);
		}
		if (comptes.findByEmail(inscription.getEmail()).isPresent()) {
			return ResultatCodeInscription.autre(Statut.EMAIL_DEJA_UTILISE, 0);
		}
		Cabinet cabinet = cabinets.save(new Cabinet(inscription.getNomCabinet(), inscription.getSiren(),
				inscription.getNombreDossiersPrevus(), inscription.getCodeParrainageSaisi(),
				inscription.getCguAccepteesLe(), maintenant));
		Compte administrateur = comptes.save(Compte.administrateurDe(cabinet, inscription.getEmail(),
				inscription.getMotDePasseHash(), inscription.nomAffiche()));
		return ResultatCodeInscription.cabinetCree(administrateur);
	}

	// Nouveau code possible après 60 secondes ; il remplace l'ancien.
	@Transactional
	public ResultatRenvoi renvoyer(String defi) {
		Optional<Inscription> trouvee = trouver(defi);
		if (trouvee.isEmpty()) {
			return ResultatRenvoi.demandeInvalide();
		}
		Inscription inscription = trouvee.get();
		Instant maintenant = horloge.instant();
		Instant renvoiPossible = inscription.getEnvoyeLe().plus(DELAI_AVANT_RENVOI);
		if (maintenant.isBefore(renvoiPossible)) {
			long secondes = (Duration.between(maintenant, renvoiPossible).toMillis() + 999) / 1000;
			return ResultatRenvoi.tropTot(secondes);
		}
		String code = Jetons.codeSixChiffres();
		inscription.remplacerCode(encodeur.encode(code), maintenant, maintenant.plus(VALIDITE_CODE));
		envoyer(inscription.getEmail(), code);
		return ResultatRenvoi.renvoye(codeDeTest(code));
	}

	private Optional<Inscription> trouver(String defi) {
		if (ValidationInscription.estVide(defi)) {
			return Optional.empty();
		}
		return inscriptions.findByEmpreinteDefi(Jetons.empreinte(defi)).filter(i -> !i.isTerminee());
	}

	private void envoyer(String email, String code) {
		envoiEmail.envoyer(email, SUJET, "Bonjour,\n\nVotre code de confirmation Pointéo est " + code
				+ ". Il est valable 5 minutes.\n\nSi vous n'avez pas demandé à créer un compte, ignorez cet email.\n");
	}

	private String codeDeTest(String code) {
		return modeTest ? code : null;
	}

	private static boolean estUnCode(String code) {
		return code != null && code.matches("\\d{6}");
	}

}
