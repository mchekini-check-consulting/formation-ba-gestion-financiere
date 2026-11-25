package com.example.gestionfinanciere.facture;

import java.util.List;

// US-12 : factures d'un onglet du dossier.
public record ListeFactures(List<LigneFacture> factures) {
}
