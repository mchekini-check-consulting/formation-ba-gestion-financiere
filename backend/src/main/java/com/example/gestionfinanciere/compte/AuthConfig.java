package com.example.gestionfinanciere.compte;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthConfig {

	// Mots de passe et codes SMS : empreinte BCrypt uniquement.
	@Bean
	public PasswordEncoder encodeurMotDePasse() {
		return new BCryptPasswordEncoder();
	}

	// Heure courante (remplaçable dans les tests : blocage 15 min, code 5 min, lien 1 h, session 30 min).
	@Bean
	public Clock horloge() {
		return Clock.systemUTC();
	}

}
