package com.example.gestionfinanciere.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Remplaçant de l'opérateur SMS tant qu'il n'est pas branché : trace l'envoi,
// sans numéro ni code (données personnelles et secret).
@Component
public class EnvoiSmsJournal implements EnvoiSms {

	private static final Logger log = LoggerFactory.getLogger(EnvoiSmsJournal.class);

	@Override
	public void envoyer(String telephone, String message) {
		log.info("SMS à envoyer (aucun opérateur SMS branché)");
	}

}
