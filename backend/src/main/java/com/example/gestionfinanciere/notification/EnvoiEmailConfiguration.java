package com.example.gestionfinanciere.notification;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

// Choix de l'envoi des emails (application.yaml, pointeo.email) :
// - serveur SMTP renseigné (SMTP_HOST) : envoi réel (EnvoiEmailSmtp) ; en dev/qualif, Mailpit de
//   la plateforme, qui garde les emails sans rien envoyer à l'extérieur ;
// - sinon (tests, preprod/prod tant qu'aucun service d'envoi n'est branché) : EnvoiEmailJournal.
@Configuration
public class EnvoiEmailConfiguration {

	// Délai maximal pour joindre le serveur SMTP, pour ne pas bloquer l'écran.
	static final String DELAI_MS = "5000";

	@Bean
	EnvoiEmail envoiEmail(@Value("${pointeo.email.smtp-host:}") String hote,
			@Value("${pointeo.email.smtp-port:1025}") int port,
			@Value("${pointeo.email.smtp-tls:false}") boolean tls,
			@Value("${pointeo.email.smtp-user:}") String utilisateur,
			@Value("${pointeo.email.smtp-password:}") String motDePasse,
			@Value("${pointeo.email.expediteur:noreply@pointeo.fr}") String expediteur) {
		if (!StringUtils.hasText(hote)) {
			return new EnvoiEmailJournal();
		}
		return new EnvoiEmailSmtp(serveur(hote, port, tls, utilisateur, motDePasse), expediteur);
	}

	static JavaMailSenderImpl serveur(String hote, int port, boolean tls, String utilisateur, String motDePasse) {
		JavaMailSenderImpl serveur = new JavaMailSenderImpl();
		serveur.setHost(hote);
		serveur.setPort(port);
		serveur.setDefaultEncoding(StandardCharsets.UTF_8.name());
		Properties proprietes = serveur.getJavaMailProperties();
		proprietes.put("mail.smtp.connectiontimeout", DELAI_MS);
		proprietes.put("mail.smtp.timeout", DELAI_MS);
		proprietes.put("mail.smtp.writetimeout", DELAI_MS);
		proprietes.put("mail.smtp.starttls.enable", String.valueOf(tls));
		// Identifiant = étiquette du mail dans Mailpit (<env>-formation-ba-gestion-financiere).
		boolean authentification = StringUtils.hasText(utilisateur);
		proprietes.put("mail.smtp.auth", String.valueOf(authentification));
		if (authentification) {
			serveur.setUsername(utilisateur);
			serveur.setPassword(motDePasse);
		}
		return serveur;
	}

}
