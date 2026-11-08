package com.example.gestionfinanciere.dossier;

// Corps de POST /dossiers (US-10). siren : 9 chiffres, espaces, points et tirets acceptés.
public record DemandeDossier(String raisonSociale, String siren) {
}
