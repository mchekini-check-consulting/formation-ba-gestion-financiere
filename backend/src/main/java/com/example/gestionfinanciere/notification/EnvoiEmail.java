package com.example.gestionfinanciere.notification;

// Envoi d'un email transactionnel : code de connexion (US-04), code de confirmation de la
// création de compte (US-36), compte bloqué (US-05), lien de réinitialisation (US-06),
// invitation (US-19). À brancher sur l'API d'envoi d'emails ; en attendant : EnvoiEmailJournal.
// Lève EnvoiImpossibleException si l'email n'a pas pu partir.
public interface EnvoiEmail {

	void envoyer(String destinataire, String sujet, String corps);

}
