package com.example.gestionfinanciere.notification;

// L'API d'envoi d'emails a renvoyé une erreur.
public class EnvoiImpossibleException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public EnvoiImpossibleException(String message, Throwable cause) {
		super(message, cause);
	}

}
