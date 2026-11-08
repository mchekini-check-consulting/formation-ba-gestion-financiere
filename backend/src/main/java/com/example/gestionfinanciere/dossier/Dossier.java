package com.example.gestionfinanciere.dossier;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.example.gestionfinanciere.cabinet.Cabinet;
import com.example.gestionfinanciere.compte.Compte;

// US-10 : dossier d'un client du cabinet (V10__dossier.sql).
@Entity
@Table(name = "dossier")
public class Dossier {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "cabinet_id", nullable = false)
	private Cabinet cabinet;

	@Column(name = "raison_sociale", nullable = false)
	private String raisonSociale;

	// RG2 : unique dans un même cabinet (contrainte en base).
	@Column(name = "siren", nullable = false)
	private String siren;

	@ManyToOne(optional = false)
	@JoinColumn(name = "cree_par_id", nullable = false)
	private Compte creePar;

	@Column(name = "cree_le", nullable = false)
	private Instant creeLe;

	protected Dossier() {
	}

	public Dossier(Cabinet cabinet, String raisonSociale, String siren, Compte creePar, Instant creeLe) {
		this.cabinet = cabinet;
		this.raisonSociale = raisonSociale;
		this.siren = siren;
		this.creePar = creePar;
		this.creeLe = creeLe;
	}

	public Long getId() {
		return id;
	}

	public Cabinet getCabinet() {
		return cabinet;
	}

	public String getRaisonSociale() {
		return raisonSociale;
	}

	public String getSiren() {
		return siren;
	}

	public Compte getCreePar() {
		return creePar;
	}

	public Instant getCreeLe() {
		return creeLe;
	}

}
