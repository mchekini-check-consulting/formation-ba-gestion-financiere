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
import com.example.gestionfinanciere.dossier.Dossier;

// Facture déposée dans un dossier client (V12__facture_et_espace_client.sql). Comptée dans la
// liste des dossiers (US-09) ; affichée par l'US-12, prévisualisée et téléchargée par l'US-13.
@Entity
@Table(name = "facture")
public class Facture {

	// Facture fournisseur (achat) ou facture client (vente).
	public enum Sens {
		ACHAT,
		VENTE
	}

	public enum Statut {
		RECUE
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "dossier_id", nullable = false)
	private Dossier dossier;

	@Enumerated(EnumType.STRING)
	@Column(name = "sens", nullable = false)
	private Sens sens;

	@Column(name = "nom_fichier", nullable = false)
	private String nomFichier;

	@Column(name = "type_fichier", nullable = false)
	private String typeFichier;

	@Column(name = "deposee_le", nullable = false)
	private Instant deposeeLe;

	@ManyToOne(optional = false)
	@JoinColumn(name = "deposee_par_id", nullable = false)
	private Compte deposeePar;

	@Enumerated(EnumType.STRING)
	@Column(name = "statut", nullable = false)
	private Statut statut;

	// Vide tant que personne du cabinet ne l'a ouverte : badge « Nouvelle » (US-12 RG3).
	@Column(name = "ouverte_le")
	private Instant ouverteLe;

	protected Facture() {
	}

	public Facture(Dossier dossier, Sens sens, String nomFichier, String typeFichier, Instant deposeeLe,
			Compte deposeePar) {
		this.dossier = dossier;
		this.sens = sens;
		this.nomFichier = nomFichier;
		this.typeFichier = typeFichier;
		this.deposeeLe = deposeeLe;
		this.deposeePar = deposeePar;
		this.statut = Statut.RECUE;
	}

	// US-12 RG3 / US-13 : ouverte par quelqu'un du cabinet, le badge « Nouvelle » disparaît.
	public void marquerOuverte(Instant quand) {
		if (ouverteLe == null) {
			ouverteLe = quand;
		}
	}

	public Long getId() {
		return id;
	}

	public Dossier getDossier() {
		return dossier;
	}

	public Sens getSens() {
		return sens;
	}

	public String getNomFichier() {
		return nomFichier;
	}

	public String getTypeFichier() {
		return typeFichier;
	}

	public Instant getDeposeeLe() {
		return deposeeLe;
	}

	public Compte getDeposeePar() {
		return deposeePar;
	}

	public Statut getStatut() {
		return statut;
	}

	public Instant getOuverteLe() {
		return ouverteLe;
	}

}
