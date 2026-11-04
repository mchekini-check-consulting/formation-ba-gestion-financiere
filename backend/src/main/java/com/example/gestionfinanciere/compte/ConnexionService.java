package com.example.gestionfinanciere.compte;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.logging.Loggable;
import com.example.gestionfinanciere.notification.EnvoiEmail;

// US-03 Se connecter (email + mot de passe) et US-05 blocage après 3 échecs.
@Service
@Loggable
public class ConnexionService {

	static final int ECHECS_AVANT_BLOCAGE = 3;
	static final Duration DUREE_BLOCAGE = Duration.ofMinutes(15);
	// Limite de BCrypt (72 octets) : au-delà, le mot de passe est forcément faux.
	private static final int OCTETS_MAX = 72;

	private final CompteRepository comptes;
	private final PasswordEncoder encodeur;
	private final Clock horloge;
	private final EnvoiEmail emails;
	private final String frontendUrl;
	// Empreinte "leurre" comparée quand l'email est inconnu : la réponse prend le même
	// temps que pour un mauvais mot de passe, on ne peut donc pas deviner quels emails existent.
	private final String empreinteLeurre;

	public ConnexionService(CompteRepository comptes, PasswordEncoder encodeur, Clock horloge, EnvoiEmail emails,
			@Value("${pointeo.frontend-url}") String frontendUrl) {
		this.comptes = comptes;
		this.encodeur = encodeur;
		this.horloge = horloge;
		this.emails = emails;
		this.frontendUrl = frontendUrl;
		this.empreinteLeurre = encodeur.encode("compte-inexistant");
	}

	/**
	 * Vérifie l'email et le mot de passe. Sans dire ce qui est faux (US-03 RG2) ; au 3e
	 * mauvais mot de passe de suite, bloque le compte 15 minutes et prévient par email (US-05).
	 */
	@Transactional
	public ResultatConnexion authentifier(String email, String motDePasse) {
		if (estVide(email) || estVide(motDePasse)) {
			return ResultatConnexion.incorrecte();
		}
		Optional<Compte> trouve = comptes.findByEmail(normaliser(email));
		boolean motDePasseOk = verifier(motDePasse, trouve.map(Compte::getMotDePasseHash).orElse(empreinteLeurre));
		if (trouve.isEmpty() || !trouve.get().isActif()) {
			return ResultatConnexion.incorrecte();
		}
		Compte compte = trouve.get();
		Instant maintenant = horloge.instant();
		if (compte.estBloque(maintenant)) {
			return ResultatConnexion.bloquee();
		}
		if (motDePasseOk) {
			compte.reinitialiserEchecs();
			return ResultatConnexion.reussie(compte);
		}
		if (compte.enregistrerEchec(maintenant, ECHECS_AVANT_BLOCAGE, DUREE_BLOCAGE)) {
			prevenirDuBlocage(compte);
			return ResultatConnexion.bloquee();
		}
		return ResultatConnexion.incorrecte();
	}

	private boolean verifier(String motDePasse, String empreinte) {
		if (motDePasse.getBytes(StandardCharsets.UTF_8).length > OCTETS_MAX) {
			return false;
		}
		return encodeur.matches(motDePasse, empreinte);
	}

	// US-05 RG2 : email qui prévient et propose de changer le mot de passe (US-06).
	private void prevenirDuBlocage(Compte compte) {
		emails.envoyer(compte.getEmail(), "Votre compte Pointéo est bloqué 15 minutes",
				"Bonjour " + compte.getNomAffiche() + ",\n\n"
						+ "Trois mots de passe incorrects ont été saisis de suite pour votre compte : "
						+ "la connexion est bloquée pendant 15 minutes.\n"
						+ "Si ce n'était pas vous, changez votre mot de passe : "
						+ frontendUrl + "/mot-de-passe-oublie\n");
	}

	// US-03 RG1 : les majuscules ne comptent pas dans l'email.
	static String normaliser(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	static boolean estVide(String valeur) {
		return valeur == null || valeur.isBlank();
	}

}
