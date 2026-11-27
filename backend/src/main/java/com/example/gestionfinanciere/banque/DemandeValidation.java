package com.example.gestionfinanciere.banque;

import java.math.BigDecimal;

/**
 * US-30 Corps de POST /dossiers/{id}/transactions/{reference}/validation : catégorie (ACHAT, VENTE,
 * FRAIS_BANCAIRES), compte du plan (vide : compte par défaut de la catégorie), taux de TVA (0 à 100).
 */
public record DemandeValidation(String categorie, String numeroCompte, BigDecimal tauxTva) {
}
