package com.example.gestionfinanciere.notification;

// Envoi d'un email transactionnel :
// compte bloqué (US-05), lien de réinitialisation (US-06),
// invitation (US-19). Envoi SMTP (EnvoiEmailSmtp) si un serveur est configuré, sinon simple
// trace (EnvoiEmailJournal) : voir EnvoiEmailConfiguration.
// Lève EnvoiImpossibleException si l'email n'a pas pu partir.
public interface EnvoiEmail {

	void envoyer(String destinataire, String sujet, String corps);

}
