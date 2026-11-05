package com.example.gestionfinanciere.cabinet;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// US-36 : cabinet d'expertise comptable, créé par son futur administrateur.
@Entity
@Table(name = "cabinet")
public class Cabinet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "nom", nullable = false)
	private String nom;

	// RG2 : unique (contrainte en base, V8__cabinet.sql).
	@Column(name = "siren", nullable = false)
	private String siren;

	@Column(name = "nombre_dossiers_prevus", nullable = false)
	private int nombreDossiersPrevus;

	@Column(name = "code_parrainage_saisi")
	private String codeParrainageSaisi;

	@Column(name = "cgu_acceptees_le", nullable = false)
	private Instant cguAccepteesLe;

	@Column(name = "cree_le", nullable = false)
	private Instant creeLe;

	protected Cabinet() {
	}

	public Cabinet(String nom, String siren, int nombreDossiersPrevus, String codeParrainageSaisi,
			Instant cguAccepteesLe, Instant creeLe) {
		this.nom = nom;
		this.siren = siren;
		this.nombreDossiersPrevus = nombreDossiersPrevus;
		this.codeParrainageSaisi = codeParrainageSaisi;
		this.cguAccepteesLe = cguAccepteesLe;
		this.creeLe = creeLe;
	}

	public Long getId() {
		return id;
	}

	public String getNom() {
		return nom;
	}

	public String getSiren() {
		return siren;
	}

	public int getNombreDossiersPrevus() {
		return nombreDossiersPrevus;
	}

	public String getCodeParrainageSaisi() {
		return codeParrainageSaisi;
	}

	public Instant getCguAccepteesLe() {
		return cguAccepteesLe;
	}

	public Instant getCreeLe() {
		return creeLe;
	}

}
