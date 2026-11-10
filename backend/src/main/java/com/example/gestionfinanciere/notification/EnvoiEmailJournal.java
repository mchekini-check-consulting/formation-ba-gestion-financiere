package com.example.gestionfinanciere.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Remplaçant de l'envoi réel quand aucun serveur SMTP n'est configuré (EnvoiEmailConfiguration) :
// trace l'envoi, sans destinataire ni contenu (données personnelles et liens secrets).
public class EnvoiEmailJournal implements EnvoiEmail {

	private static final Logger log = LoggerFactory.getLogger(EnvoiEmailJournal.class);

	@Override
	public void envoyer(String destinataire, String sujet, String corps) {
		log.info("email à envoyer (aucun serveur d'envoi configuré)");
	}

}
