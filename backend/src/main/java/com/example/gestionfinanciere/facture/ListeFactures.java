package com.example.gestionfinanciere.facture;

import java.util.List;

// US-12 : factures d'un onglet du dossier. exemplesPossibles : le bouton « Ajouter des factures
// d'exemple » n'est proposé qu'en mode test (dev, qualif), jamais en preprod ni en prod.
public record ListeFactures(List<LigneFacture> factures, boolean exemplesPossibles) {
}
