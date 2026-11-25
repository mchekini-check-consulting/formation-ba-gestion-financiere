package com.example.gestionfinanciere.facture;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.example.gestionfinanciere.compte.Compte;

// US-55 RG8 : une modification des informations d'une facture, avec son auteur, sa date et les
// champs modifiés (V26__correction_facture.sql). Supprimée avec sa facture (US-16).
@Entity
@Table(name = "facture_correction")
public class CorrectionFacture {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "facture_id", nullable = false)
	private Facture facture;

	// Noms des champs modifiés (TYPE_TIERS, MONTANT_HT...), séparés par des virgules.
	@Column(name = "champs", nullable = false)
	private String champs;

	@ManyToOne(optional = false)
	@JoinColumn(name = "modifie_par_id", nullable = false)
	private Compte modifiePar;

	@Column(name = "modifie_le", nullable = false)
	private Instant modifieLe;

	protected CorrectionFacture() {
	}

	public CorrectionFacture(Facture facture, String champs, Compte modifiePar, Instant modifieLe) {
		this.facture = facture;
		this.champs = champs;
		this.modifiePar = modifiePar;
		this.modifieLe = modifieLe;
	}

	public Long getId() {
		return id;
	}

	public Facture getFacture() {
		return facture;
	}

	public String getChamps() {
		return champs;
	}

	public Compte getModifiePar() {
		return modifiePar;
	}

	public Instant getModifieLe() {
		return modifieLe;
	}

}
