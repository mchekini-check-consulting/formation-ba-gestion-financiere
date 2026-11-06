package com.example.gestionfinanciere.equipe;

// Corps de POST /equipe/invitations (US-19). role : ADMINISTRATEUR, ASSOCIE ou COMPTABLE (RG2).
public record DemandeInvitation(String prenom, String nom, String email, String role) {
}
