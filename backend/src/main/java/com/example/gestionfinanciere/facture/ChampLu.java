package com.example.gestionfinanciere.facture;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// US-53 RG4 : niveau de confiance d'un champ lu sur une facture (V23__lecture_facture.sql) ;
// aVerifier : doute (confiance basse ou montants incohérents, RG5). Supprimé avec sa facture.
@Entity
@Table(name = "facture_champ_lu")
public class ChampLu {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "facture_id", nullable = false)
	private Facture facture;

	@Enumerated(EnumType.STRING)
	@Column(name = "champ", nullable = false)
	private ChampFacture champ;

	@Column(name = "confiance", nullable = false, precision = 4, scale = 3)
	private BigDecimal confiance;

	@Column(name = "a_verifier", nullable = false)
	private boolean aVerifier;

	protected ChampLu() {
	}

	public ChampLu(Facture facture, ChampFacture champ, double confiance, boolean aVerifier) {
		this.facture = facture;
		this.champ = champ;
		this.confiance = BigDecimal.valueOf(confiance).setScale(3, RoundingMode.HALF_UP);
		this.aVerifier = aVerifier;
	}

	// US-55 RG2 : corrigé ou validé par le comptable, le champ n'est plus « À vérifier ».
	void marquerVerifie() {
		aVerifier = false;
	}

	public Long getId() {
		return id;
	}

	public Facture getFacture() {
		return facture;
	}

	public ChampFacture getChamp() {
		return champ;
	}

	public BigDecimal getConfiance() {
		return confiance;
	}

	public boolean isAVerifier() {
		return aVerifier;
	}

}
