package com.example.gestionfinanciere.plancomptable;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// US-41 Compte du plan comptable d'un dossier (V20__plan_comptable.sql) : compte du plan général
// copié à la création du dossier (RG1) ou sous-compte ajouté par la comptable (ex. 401MOULINS).
@Entity
@Table(name = "compte_comptable")
public class CompteComptable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "dossier_id", nullable = false)
	private Long dossierId;

	@Column(name = "numero", nullable = false)
	private String numero;

	@Column(name = "libelle", nullable = false)
	private String libelle;

	@Column(name = "sous_compte", nullable = false)
	private boolean sousCompte;

	// RG3 : un compte désactivé reste dans le plan mais ne se choisit plus dans une écriture.
	@Column(name = "actif", nullable = false)
	private boolean actif;

	@Column(name = "cree_le", nullable = false)
	private Instant creeLe;

	protected CompteComptable() {
	}

	private CompteComptable(Long dossierId, String numero, String libelle, boolean sousCompte, Instant creeLe) {
		this.dossierId = dossierId;
		this.numero = numero;
		this.libelle = libelle;
		this.sousCompte = sousCompte;
		this.actif = true;
		this.creeLe = creeLe;
	}

	// RG1 : sous-compte ajouté par la comptable, actif dès sa création.
	public static CompteComptable sousCompte(Long dossierId, String numero, String libelle, Instant creeLe) {
		return new CompteComptable(dossierId, numero, libelle, true, creeLe);
	}

	// RG1 : compte copié du plan comptable général.
	public static CompteComptable general(Long dossierId, String numero, String libelle, Instant creeLe) {
		return new CompteComptable(dossierId, numero, libelle, false, creeLe);
	}

	public Long getId() {
		return id;
	}

	public Long getDossierId() {
		return dossierId;
	}

	public String getNumero() {
		return numero;
	}

	public String getLibelle() {
		return libelle;
	}

	public boolean isSousCompte() {
		return sousCompte;
	}

	public boolean isActif() {
		return actif;
	}

	public void setActif(boolean actif) {
		this.actif = actif;
	}

	public Instant getCreeLe() {
		return creeLe;
	}

}
