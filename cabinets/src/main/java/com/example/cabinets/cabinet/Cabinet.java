package com.example.cabinets.cabinet;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

// Compte d'un cabinet d'expertise comptable (table créée par db/migration/V2__cabinet.sql)
@Entity
@Table(name = "cabinet")
public class Cabinet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String nom;

	@Column(nullable = false, length = 9, unique = true)
	private String siren;

	@Column(nullable = false, length = 254)
	private String email;

	@Column(name = "nombre_dossiers", nullable = false)
	private int nombreDossiers;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private OffsetDateTime creeLe;

	protected Cabinet() {
	}

	public Cabinet(String nom, String siren, String email, int nombreDossiers) {
		modifier(nom, siren, email, nombreDossiers);
	}

	public final void modifier(String nom, String siren, String email, int nombreDossiers) {
		this.nom = nom;
		this.siren = siren;
		this.email = email;
		this.nombreDossiers = nombreDossiers;
	}

	@PrePersist
	void avantCreation() {
		creeLe = OffsetDateTime.now();
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

	public String getEmail() {
		return email;
	}

	public int getNombreDossiers() {
		return nombreDossiers;
	}

	public OffsetDateTime getCreeLe() {
		return creeLe;
	}

}
