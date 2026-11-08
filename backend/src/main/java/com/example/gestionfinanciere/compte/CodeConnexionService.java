package com.example.gestionfinanciere.compte;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.logging.Loggable;
import com.example.gestionfinanciere.notification.EnvoiEmail;

// US-04 Valider la connexion par un code reçu par email.
@Service
@Loggable
public class CodeConnexionService {

	static final Duration VALIDITE_CODE = Duration.ofMinutes(5);
	static final Duration DELAI_AVANT_RENVOI = Duration.ofSeconds(60);
	static final int CODES_FAUX_MAX = 3;
	static final String SUJET = "Votre code de connexion Pointéo";

	private final CodeConnexionRepository codes;
	private final PasswordEncoder encodeur;
	private final Clock horloge;
	private final EnvoiEmail email;
	private final boolean modeTest;

	public CodeConnexionService(CodeConnexionRepository codes, PasswordEncoder encodeur, Clock horloge,
			EnvoiEmail email, @Value("${pointeo.mode-test:false}") boolean modeTest) {
		this.codes = codes;
		this.encodeur = encodeur;
		this.horloge = horloge;
		this.email = email;
		this.modeTest = modeTest;
	}

	// Après le bon mot de passe : crée un code (RG1 : 6 chiffres, 5 minutes) et l'envoie par email.
	@Transactional
	public DefiCode demarrer(Compte compte) {
		String defi = Jetons.nouveau();
		String code = Jetons.codeSixChiffres();
		Instant maintenant = horloge.instant();
		codes.save(new CodeConnexion(compte, Jetons.empreinte(defi), encodeur.encode(code), maintenant,
				maintenant.plus(VALIDITE_CODE)));
		envoyer(compte, code);
		return new DefiCode(defi, compte.emailMasque(), codeDeTest(code));
	}

	@Transactional
	public ResultatCode verifier(String defi, String code) {
		Optional<CodeConnexion> trouve = trouver(defi);
		if (trouve.isEmpty()) {
			return ResultatCode.demandeInvalide();
		}
		CodeConnexion demande = trouve.get();
		if (demande.estExpire(horloge.instant())) {
			return ResultatCode.expire(CODES_FAUX_MAX - demande.getCodesFaux());
		}
		if (estUnCode(code) && encodeur.matches(code, demande.getEmpreinteCode())) {
			demande.terminer(); // RG1 : le code ne sert qu'une fois
			return ResultatCode.reussi(demande.getCompte());
		}
		int restantes = CODES_FAUX_MAX - demande.enregistrerCodeFaux();
		if (restantes <= 0) {
			demande.terminer(); // RG3 : retour à l'écran de connexion
			return ResultatCode.tropDeCodesFaux();
		}
		return ResultatCode.faux(restantes);
	}

	// RG2 : nouveau code possible après 60 secondes ; il remplace l'ancien.
	@Transactional
	public ResultatRenvoi renvoyer(String defi) {
		Optional<CodeConnexion> trouve = trouver(defi);
		if (trouve.isEmpty()) {
			return ResultatRenvoi.demandeInvalide();
		}
		CodeConnexion demande = trouve.get();
		Instant maintenant = horloge.instant();
		Instant renvoiPossible = demande.getEnvoyeLe().plus(DELAI_AVANT_RENVOI);
		if (maintenant.isBefore(renvoiPossible)) {
			long secondes = (Duration.between(maintenant, renvoiPossible).toMillis() + 999) / 1000;
			return ResultatRenvoi.tropTot(secondes);
		}
		String code = Jetons.codeSixChiffres();
		demande.remplacerCode(encodeur.encode(code), maintenant, maintenant.plus(VALIDITE_CODE));
		envoyer(demande.getCompte(), code);
		return ResultatRenvoi.renvoye(codeDeTest(code));
	}

	private Optional<CodeConnexion> trouver(String defi) {
		if (ConnexionService.estVide(defi)) {
			return Optional.empty();
		}
		return codes.findByEmpreinteDefi(Jetons.empreinte(defi)).filter(c -> !c.isTermine());
	}

	private void envoyer(Compte compte, String code) {
		email.envoyer(compte.getEmail(), SUJET, "Bonjour,\n\nVotre code de connexion Pointéo est " + code
				+ ". Il est valable 5 minutes.\n\nSi vous n'êtes pas à l'origine de cette connexion, "
				+ "changez votre mot de passe.\n");
	}

	private String codeDeTest(String code) {
		return modeTest ? code : null;
	}

	private static boolean estUnCode(String code) {
		return code != null && code.matches("\\d{6}");
	}

}
