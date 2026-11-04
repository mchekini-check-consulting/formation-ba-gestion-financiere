package com.example.gestionfinanciere.notification;

// Envoi d'un email (US-05 compte bloqué, US-06 lien de réinitialisation).
// À brancher sur l'API d'envoi d'emails ; en attendant : EnvoiEmailJournal.
public interface EnvoiEmail {

	void envoyer(String destinataire, String sujet, String corps);

}
