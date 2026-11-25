package com.example.gestionfinanciere.facture;

import java.time.Instant;

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

import com.example.gestionfinanciere.compte.Compte;

// US-55 RG8 : un champ de la fiche facture modifié par le cabinet, avec l'ancienne et la nouvelle
// valeur, son auteur et sa date (V26__correction_facture.sql). Supprimé avec sa facture (US-16).
@Entity
@Table(name = "facture_modification")
public class ModificationFacture {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "facture_id", nullable = false)
	private Facture facture;

	@Enumerated(EnumType.STRING)
	@Column(name = "champ", nullable = false)
	private ChampFacture champ;

	@Column(name = "ancienne_valeur")
	private String ancienneValeur;

	@Column(name = "nouvelle_valeur")
	private String nouvelleValeur;

	@ManyToOne(optional = false)
	@JoinColumn(name = "modifie_par_id", nullable = false)
	private Compte modifiePar;

	@Column(name = "modifie_le", nullable = false)
	private Instant modifieLe;

	protected ModificationFacture() {
	}

	public ModificationFacture(Facture facture, InformationsFacture.Changement changement, Compte modifiePar,
			Instant modifieLe) {
		this.facture = facture;
		this.champ = changement.champ();
		this.ancienneValeur = changement.ancienneValeur();
		this.nouvelleValeur = changement.nouvelleValeur();
		this.modifiePar = modifiePar;
		this.modifieLe = modifieLe;
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

	public String getAncienneValeur() {
		return ancienneValeur;
	}

	public String getNouvelleValeur() {
		return nouvelleValeur;
	}

	public Compte getModifiePar() {
		return modifiePar;
	}

	public Instant getModifieLe() {
		return modifieLe;
	}

}
