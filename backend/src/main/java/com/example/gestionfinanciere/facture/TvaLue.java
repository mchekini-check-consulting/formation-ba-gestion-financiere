package com.example.gestionfinanciere.facture;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.example.gestionfinanciere.facture.lecture.TvaParTaux;

// US-54 CA2 : TVA lue pour un taux, sur une facture à plusieurs taux (V24__lecture_facture_client.sql).
// Supprimée avec sa facture ; une nouvelle lecture remplace le détail.
@Entity
@Table(name = "facture_tva_lue")
public class TvaLue {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "facture_id", nullable = false)
	private Facture facture;

	@Column(name = "taux", nullable = false, precision = 5, scale = 2)
	private BigDecimal taux;

	@Column(name = "base_ht", precision = 12, scale = 2)
	private BigDecimal baseHt;

	@Column(name = "montant_tva", nullable = false, precision = 12, scale = 2)
	private BigDecimal montantTva;

	protected TvaLue() {
	}

	TvaLue(Facture facture, TvaParTaux lue) {
		this.facture = facture;
		this.taux = lue.taux();
		this.baseHt = lue.baseHt();
		this.montantTva = lue.montantTva();
	}

	public TvaParTaux enValeur() {
		return new TvaParTaux(taux, baseHt, montantTva);
	}

}
