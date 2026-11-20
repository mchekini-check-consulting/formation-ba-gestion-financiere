package com.example.gestionfinanciere.plancomptable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// US-41 RG2 Catégorie (Achat, Vente, Frais bancaires) et son compte par défaut
// (V20__plan_comptable.sql). La liste se règle par migration, comme les modules (US-18).
@Entity
@Table(name = "categorie")
public class Categorie {

	@Id
	private Long id;

	@Column(name = "code", nullable = false)
	private String code;

	@Column(name = "libelle", nullable = false)
	private String libelle;

	@Column(name = "numero_compte", nullable = false)
	private String numeroCompte;

	@Column(name = "ordre", nullable = false)
	private int ordre;

	protected Categorie() {
	}

	public Categorie(String code, String libelle, String numeroCompte, int ordre) {
		this.code = code;
		this.libelle = libelle;
		this.numeroCompte = numeroCompte;
		this.ordre = ordre;
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getLibelle() {
		return libelle;
	}

	public String getNumeroCompte() {
		return numeroCompte;
	}

	public int getOrdre() {
		return ordre;
	}

}
