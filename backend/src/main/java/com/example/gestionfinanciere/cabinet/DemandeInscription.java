package com.example.gestionfinanciere.cabinet;

// Corps de POST /inscription (US-36 RG2), tel que saisi dans le formulaire « Créer un compte ».
public record DemandeInscription(String nomCabinet, String siren, String prenom, String nom, String email,
		String motDePasse, Integer nombreDossiers, String codeParrainage, Boolean cguAcceptees) {
}
