package com.example.gestionfinanciere.facture;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.dossier.Dossier;
import com.example.gestionfinanciere.facture.lecture.ResultatLecture;

// Facture déposée dans un dossier client (V12__facture_et_espace_client.sql). Comptée dans la
// liste des dossiers (US-09) ; affichée par l'US-12, prévisualisée et téléchargée par l'US-13.
// @DynamicUpdate : la lecture (US-53, en arrière-plan) ne réécrit que ses colonnes, jamais le statut ou
// l'ouverture changés au même moment par le cabinet.
@Entity
@DynamicUpdate
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

	// US-53 RG6 : statut de la lecture automatique d'une facture fournisseur.
	public enum StatutLecture {
		EN_COURS,
		LUE,
		ECHOUEE
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

	// US-53 : lecture automatique (vide si la facture n'est pas lue : facture client, déposée avant l'US-53).
	@Enumerated(EnumType.STRING)
	@Column(name = "lecture_statut")
	private StatutLecture lectureStatut;

	@Column(name = "lecture_terminee_le")
	private Instant lectureTermineeLe;

	@Column(name = "lecture_a_verifier")
	private Boolean lectureAVerifier;

	// US-53 RG2 : champs lus.
	@Column(name = "type_tiers")
	private String typeTiers;

	@Column(name = "nom_tiers")
	private String nomTiers;

	@Column(name = "numero_facture")
	private String numeroFacture;

	@Column(name = "date_emission")
	private LocalDate dateEmission;

	@Column(name = "montant_ht", precision = 12, scale = 2)
	private BigDecimal montantHt;

	@Column(name = "montant_tva", precision = 12, scale = 2)
	private BigDecimal montantTva;

	@Column(name = "taux_tva", precision = 5, scale = 2)
	private BigDecimal tauxTva;

	@Column(name = "montant_ttc", precision = 12, scale = 2)
	private BigDecimal montantTtc;

	@Column(name = "compte_contrepartie")
	private String compteContrepartie;

	// US-54 CA2 : plusieurs taux de TVA (taux_tva vide, « Multiple ») et détail par taux.
	@Column(name = "taux_tva_multiple")
	private Boolean tauxTvaMultiple;

	@OneToMany(mappedBy = "facture", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("taux desc")
	private List<TvaLue> detailTva = new ArrayList<>();

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

	/**
	 * US-53 RG1 (factures fournisseurs) et US-54 RG1 (factures clients) : une facture avec son fichier
	 * est mise en lecture dès son arrivée. Vrai si la lecture est demandée ; faux sans fichier.
	 */
	public boolean demarrerLecture() {
		if (contenu == null) {
			return false;
		}
		lectureStatut = StatutLecture.EN_COURS;
		return true;
	}

	// US-53 RG2 à RG6 : résultat de la lecture ; illisible -> « Lecture échouée » (CA4), aucun champ.
	public void terminerLecture(ResultatLecture resultat, Instant quand) {
		lectureTermineeLe = quand;
		detailTva.clear();
		if (!resultat.lisible()) {
			lectureStatut = StatutLecture.ECHOUEE;
			lectureAVerifier = null;
			tauxTvaMultiple = null;
			return;
		}
		lectureStatut = StatutLecture.LUE;
		lectureAVerifier = resultat.aVerifierOuARenseigner();
		typeTiers = resultat.typeTiers();
		nomTiers = resultat.nomTiers();
		numeroFacture = resultat.numeroFacture();
		dateEmission = resultat.dateEmission();
		montantHt = resultat.montantHt();
		montantTva = resultat.montantTva();
		tauxTva = resultat.tauxTva();
		montantTtc = resultat.montantTtc();
		compteContrepartie = resultat.compteContrepartie();
		tauxTvaMultiple = resultat.tauxMultiple();
		resultat.detailTva().forEach(lue -> detailTva.add(new TvaLue(this, lue)));
	}

	/**
	 * US-55 RG8 : une facture traitée (validée) est en lecture seule, comme le sera une facture exportée
	 * (US-40) ; pendant la lecture automatique, la fiche attend son résultat pour ne pas être écrasée.
	 */
	public boolean isModifiable() {
		return statut != Statut.TRAITEE && lectureStatut != StatutLecture.EN_COURS;
	}

	// US-55 RG2 et RG7 : champs corrigés ou saisis à la main par le comptable (valeurs déjà nettoyées).
	void corriger(InformationsFacture infos) {
		typeTiers = infos.typeTiers();
		nomTiers = infos.nomTiers();
		numeroFacture = infos.numeroFacture();
		dateEmission = infos.dateEmission();
		montantHt = infos.montantHt();
		montantTva = infos.montantTva();
		tauxTva = infos.tauxTva();
		montantTtc = infos.montantTtc();
		compteContrepartie = infos.compteContrepartie();
	}

	// US-53 : pastille « À vérifier » de la liste, mise à jour après une correction (US-55) ; seulement
	// pour une facture lue, une facture jamais lue ou en lecture échouée n'a pas de pastille.
	void mettreAJourAVerifier(boolean resteAVerifier) {
		if (lectureStatut == StatutLecture.LUE) {
			lectureAVerifier = resteAVerifier;
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

	public String getMotifACompleter() {
		return motifACompleter;
	}

	public Instant getOuverteLe() {
		return ouverteLe;
	}

	public StatutLecture getLectureStatut() {
		return lectureStatut;
	}

	public Instant getLectureTermineeLe() {
		return lectureTermineeLe;
	}

	public boolean isLectureAVerifier() {
		return Boolean.TRUE.equals(lectureAVerifier);
	}

	public String getTypeTiers() {
		return typeTiers;
	}

	public String getNomTiers() {
		return nomTiers;
	}

	public String getNumeroFacture() {
		return numeroFacture;
	}

	public LocalDate getDateEmission() {
		return dateEmission;
	}

	public BigDecimal getMontantHt() {
		return montantHt;
	}

	public BigDecimal getMontantTva() {
		return montantTva;
	}

	public BigDecimal getTauxTva() {
		return tauxTva;
	}

	public BigDecimal getMontantTtc() {
		return montantTtc;
	}

	public String getCompteContrepartie() {
		return compteContrepartie;
	}

	public boolean isTauxTvaMultiple() {
		return Boolean.TRUE.equals(tauxTvaMultiple);
	}

	public List<TvaLue> getDetailTva() {
		return List.copyOf(detailTva);
	}

	public byte[] getContenu() {
		return contenu == null ? null : contenu.clone();
	}

	public Source getSource() {
		return source;
	}

}
