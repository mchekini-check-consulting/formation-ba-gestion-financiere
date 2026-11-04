package com.example.gestionfinanciere.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Remplaçant de l'API d'envoi d'emails tant qu'elle n'est pas branchée : trace l'envoi,
// sans destinataire ni contenu (données personnelles et liens secrets).
@Component
public class EnvoiEmailJournal implements EnvoiEmail {

	private static final Logger log = LoggerFactory.getLogger(EnvoiEmailJournal.class);

	@Override
	public void envoyer(String destinataire, String sujet, String corps) {
		log.info("email à envoyer (aucune API d'envoi branchée)");
	}

}
