package com.example.gestionfinanciere.facture.lecture;

// US-53 RG1 : une facture fournisseur vient d'arriver ; sa lecture démarre une fois le dépôt enregistré.
public record DemandeLecture(Long factureId) {
}
