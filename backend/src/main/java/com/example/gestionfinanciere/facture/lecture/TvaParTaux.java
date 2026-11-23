package com.example.gestionfinanciere.facture.lecture;

import java.math.BigDecimal;

// US-54 CA2 : TVA lue pour un taux (base HT vide si la facture ne la donne pas).
public record TvaParTaux(BigDecimal taux, BigDecimal baseHt, BigDecimal montantTva) {
}
