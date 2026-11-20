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

// US-51 RG4 : un changement de statut d'une facture, avec son auteur et sa date
// (V20__statut_facture.sql). Supprimé avec sa facture (US-16).
@Entity
@Table(name = "facture_statut_historique")
public class ChangementStatutFacture {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "facture_id", nullable = false)
	private Facture facture;

	@Enumerated(EnumType.STRING)
	@Column(name = "ancien_statut", nullable = false)
	private Facture.Statut ancienStatut;

	@Enumerated(EnumType.STRING)
	@Column(name = "nouveau_statut", nullable = false)
	private Facture.Statut nouveauStatut;

	@Column(name = "motif")
	private String motif;

	@ManyToOne(optional = false)
	@JoinColumn(name = "modifie_par_id", nullable = false)
	private Compte modifiePar;

	@Column(name = "modifie_le", nullable = false)
	private Instant modifieLe;

	protected ChangementStatutFacture() {
	}

	public ChangementStatutFacture(Facture facture, Facture.Statut ancienStatut, Facture.Statut nouveauStatut,
			String motif, Compte modifiePar, Instant modifieLe) {
		this.facture = facture;
		this.ancienStatut = ancienStatut;
		this.nouveauStatut = nouveauStatut;
		this.motif = motif;
		this.modifiePar = modifiePar;
		this.modifieLe = modifieLe;
	}

	public Long getId() {
		return id;
	}

	public Facture getFacture() {
		return facture;
	}

	public Facture.Statut getAncienStatut() {
		return ancienStatut;
	}

	public Facture.Statut getNouveauStatut() {
		return nouveauStatut;
	}

	public String getMotif() {
		return motif;
	}

	public Compte getModifiePar() {
		return modifiePar;
	}

	public Instant getModifieLe() {
		return modifieLe;
	}

}
