package com.example.gestionfinanciere.notification;

// Envoi d'un SMS (US-04 code de vérification). À brancher sur l'API d'un opérateur SMS ;
// en attendant : EnvoiSmsJournal. Lève EnvoiImpossibleException si l'opérateur refuse.
public interface EnvoiSms {

	void envoyer(String telephone, String message);

}
