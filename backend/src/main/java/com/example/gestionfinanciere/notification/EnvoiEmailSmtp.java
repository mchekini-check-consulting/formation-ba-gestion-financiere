package com.example.gestionfinanciere.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

// Envoi réel d'un email texte par SMTP (Mailpit en dev/qualif et en local). Trace l'envoi
// sans destinataire ni contenu (données personnelles et liens secrets).
public class EnvoiEmailSmtp implements EnvoiEmail {

	private static final Logger log = LoggerFactory.getLogger(EnvoiEmailSmtp.class);

	private final JavaMailSender serveur;

	private final String expediteur;

	public EnvoiEmailSmtp(JavaMailSender serveur, String expediteur) {
		this.serveur = serveur;
		this.expediteur = expediteur;
	}

	@Override
	public void envoyer(String destinataire, String sujet, String corps) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(expediteur);
		message.setTo(destinataire);
		message.setSubject(sujet);
		message.setText(corps);
		try {
			serveur.send(message);
		}
		catch (MailException e) {
			log.warn("échec de l'envoi d'un email");
			throw new EnvoiImpossibleException("envoi de l'email impossible", e);
		}
		log.info("email envoyé");
	}

}
