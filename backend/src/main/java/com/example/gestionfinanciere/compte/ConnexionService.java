package com.example.gestionfinanciere.compte;

import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.gestionfinanciere.logging.Loggable;

// US-03 Se connecter : vérifie l'email et le mot de passe d'un compte.
@Service
@Loggable
public class ConnexionService {

	private final CompteRepository comptes;
	private final PasswordEncoder encodeur;
	// Empreinte "leurre" comparée quand l'email est inconnu : la réponse prend le même
	// temps que pour un mauvais mot de passe, on ne peut donc pas deviner quels emails existent (RG2).
	private final String empreinteLeurre;

	@Autowired
	public ConnexionService(CompteRepository comptes) {
		this(comptes, new BCryptPasswordEncoder());
	}

	ConnexionService(CompteRepository comptes, PasswordEncoder encodeur) {
		this.comptes = comptes;
		this.encodeur = encodeur;
		this.empreinteLeurre = encodeur.encode("compte-inexistant");
	}

	/**
	 * Rôle du compte si l'email et le mot de passe sont bons et le compte actif ;
	 * vide sinon, sans dire ce qui est faux (RG2).
	 */
	public Optional<Role> authentifier(String email, String motDePasse) {
		if (estVide(email) || estVide(motDePasse)) {
			return Optional.empty();
		}
		Optional<Compte> compte = comptes.findByEmail(normaliser(email));
		String empreinte = compte.map(Compte::getMotDePasseHash).orElse(empreinteLeurre);
		boolean motDePasseOk = encodeur.matches(motDePasse, empreinte);
		return compte.filter(c -> motDePasseOk && c.isActif()).map(Compte::getRole);
	}

	// RG1 : les majuscules ne comptent pas dans l'email.
	static String normaliser(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private static boolean estVide(String valeur) {
		return valeur == null || valeur.isBlank();
	}

}
