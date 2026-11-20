package com.example.gestionfinanciere.facture;

import java.time.Instant;
import java.util.List;

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

	// US-51 RG1 : statut de traitement de la facture par le cabinet.
	public enum Statut {
		RECUE,
		EN_COURS,
		TRAITEE,
		A_COMPLETER;

		// RG2 : Reçue -> En cours de traitement -> Traitée ; Reçue ou En cours -> À compléter ;
		// À compléter -> En cours de traitement (quand le client a redéposé ou complété).
		public List<Statut> suivants() {
			return switch (this) {
				case RECUE -> List.of(EN_COURS, A_COMPLETER);
				case EN_COURS -> List.of(TRAITEE, A_COMPLETER);
				case A_COMPLETER -> List.of(EN_COURS);
				case TRAITEE -> List.of();
			};
		}
	}

	// US-52 RG4 (V22__source_facture.sql) : déposée par le dirigeant ou importée par le cabinet.
	public enum Source {
		DEPOT_CLIENT,
		IMPORT_CABINET
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

	// US-51 RG3 : motif du statut « À compléter », affiché au client ; vide pour les autres statuts.
	@Column(name = "motif_a_completer")
	private String motifACompleter;

	// Vide tant que personne du cabinet ne l'a ouverte : badge « Nouvelle » (US-12 RG3).
	@Column(name = "ouverte_le")
	private Instant ouverteLe;

	// US-13 : fichier d'origine (PDF ou image) ; vide si la facture n'en a pas.
	@Column(name = "contenu")
	private byte[] contenu;

	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false)
	private Source source = Source.DEPOT_CLIENT;

	protected Facture() {
	}

	// US-52 : facture importée par un collaborateur du cabinet dans le dossier (RG4 « Import cabinet »).
	public static Facture importee(Dossier dossier, Sens sens, String nomFichier, String typeFichier,
			Instant importeeLe, Compte collaborateur, byte[] contenu) {
		Facture facture = new Facture(dossier, sens, nomFichier, typeFichier, importeeLe, collaborateur, contenu);
		facture.source = Source.IMPORT_CABINET;
		return facture;
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

	// US-13 : facture avec son fichier d'origine.
	public Facture(Dossier dossier, Sens sens, String nomFichier, String typeFichier, Instant deposeeLe,
			Compte deposeePar, byte[] contenu) {
		this(dossier, sens, nomFichier, typeFichier, deposeeLe, deposeePar);
		this.contenu = contenu == null ? null : contenu.clone();
	}

	// US-12 RG3 / US-13 : ouverte par quelqu'un du cabinet, le badge « Nouvelle » disparaît.
	public void marquerOuverte(Instant quand) {
		if (ouverteLe == null) {
			ouverteLe = quand;
		}
	}

	// US-51 RG2 et RG3 : vrai si le passage est autorisé ; le motif n'est gardé que pour « À compléter ».
	boolean changerStatut(Statut nouveau, String motif) {
		if (!statut.suivants().contains(nouveau)) {
			return false;
		}
		statut = nouveau;
		motifACompleter = nouveau == Statut.A_COMPLETER ? motif : null;
		return true;
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

	public String getMotifACompleter() {
		return motifACompleter;
	}

	public Instant getOuverteLe() {
		return ouverteLe;
	}

	public byte[] getContenu() {
		return contenu == null ? null : contenu.clone();
	}

	public Source getSource() {
		return source;
	}

}
