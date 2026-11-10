package com.example.gestionfinanciere.module;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// US-18 Module proposé dans les dossiers (table créée par db/migration/V14__module.sql).
// RG3 : la liste est réglée en base ; un nouveau module s'ajoute par une migration.
@Entity
@Table(name = "module")
public class ModuleDossier {

	// RG1 : seul statut pour l'instant, affiché « Bientôt disponible ».
	public enum Statut {
		BIENTOT_DISPONIBLE
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String code;

	@Column(nullable = false, length = 100)
	private String nom;

	@Column(length = 255)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private Statut statut;

	@Column(nullable = false)
	private Integer ordre;

	protected ModuleDossier() {
	}

	public ModuleDossier(String code, String nom, String description, Statut statut, int ordre) {
		this.code = code;
		this.nom = nom;
		this.description = description;
		this.statut = statut;
		this.ordre = ordre;
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getNom() {
		return nom;
	}

	public String getDescription() {
		return description;
	}

	public Statut getStatut() {
		return statut;
	}

	public Integer getOrdre() {
		return ordre;
	}

}
