package com.example.gestionfinanciere.ecriture;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

// US-56 Écriture d'une facture d'achat validée (V27__ecriture.sql), au journal des achats.
// RG5 : re-générée (lignes remplacées) tant qu'elle n'est pas exportée. Supprimée avec sa facture.
// US-47 (V28__journal_ecritures.sql) : n° de pièce, correction à la main tant qu'elle n'est pas exportée.
@Entity
@Table(name = "ecriture")
public class Ecriture {

	// Journal des achats.
	public enum Journal {
		AC
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "dossier_id", nullable = false)
	private Long dossierId;

	@Column(name = "facture_id", nullable = false)
	private Long factureId;

	@Enumerated(EnumType.STRING)
	@Column(name = "journal", nullable = false)
	private Journal journal;

	@Column(name = "date_ecriture", nullable = false)
	private LocalDate dateEcriture;

	// US-47 RG1 : n° de la facture.
	@Column(name = "numero_piece")
	private String numeroPiece;

	@Column(name = "libelle", nullable = false)
	private String libelle;

	@Column(name = "generee_le", nullable = false)
	private Instant genereeLe;

	@ManyToOne(optional = false)
	@JoinColumn(name = "generee_par_id", nullable = false)
	private Compte genereePar;

	// Vide tant que l'écriture n'est pas exportée (export : EP-10).
	@Column(name = "exportee_le")
	private Instant exporteeLe;

	// US-47 RG4 : dernière correction à la main ; vide si l'écriture est telle que générée.
	@Column(name = "modifiee_le")
	private Instant modifieeLe;

	@ManyToOne
	@JoinColumn(name = "modifiee_par_id")
	private Compte modifieePar;

	@OneToMany(mappedBy = "ecriture", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("ordre")
	private List<LigneEcriture> lignes = new ArrayList<>();

	protected Ecriture() {
	}

	// RG1 : écriture d'une facture d'achat, au journal des achats.
	public static Ecriture achat(Long dossierId, Long factureId) {
		Ecriture ecriture = new Ecriture();
		ecriture.dossierId = dossierId;
		ecriture.factureId = factureId;
		ecriture.journal = Journal.AC;
		return ecriture;
	}

	// RG1, RG3, RG4 ; RG5 : les lignes précédentes sont remplacées, une correction à la main aussi (US-47).
	void remplir(CalculEcriture.Resultat calcul, Compte auteur, Instant quand) {
		dateEcriture = calcul.date();
		numeroPiece = calcul.piece();
		libelle = calcul.libelle();
		genereePar = auteur;
		genereeLe = quand;
		modifieePar = null;
		modifieeLe = null;
		lignes.clear();
		List<CalculEcriture.Ligne> calculees = calcul.lignes();
		for (int ordre = 0; ordre < calculees.size(); ordre++) {
			lignes.add(new LigneEcriture(this, ordre + 1, calculees.get(ordre)));
		}
	}

	/**
	 * US-47 RG2, RG4 : correction à la main (libellé, et compte, débit, crédit de chaque ligne, dans l'ordre).
	 * Le libellé d'une ligne qui reprenait celui de l'écriture suit le nouveau. L'appelant a vérifié
	 * qu'elle n'est pas exportée, qu'elle garde le même nombre de lignes et qu'elle est équilibrée.
	 */
	void corriger(String nouveauLibelle, List<CalculEcriture.Ligne> corrigees, Compte auteur, Instant quand) {
		for (int i = 0; i < lignes.size(); i++) {
			CalculEcriture.Ligne corrigee = corrigees.get(i);
			LigneEcriture ligne = lignes.get(i);
			String libelleLigne = ligne.getLibelle().startsWith(libelle)
					? nouveauLibelle + ligne.getLibelle().substring(libelle.length())
					: ligne.getLibelle();
			ligne.corriger(corrigee.numeroCompte(), libelleLigne, corrigee.debit(), corrigee.credit());
		}
		libelle = nouveauLibelle;
		modifieePar = auteur;
		modifieeLe = quand;
	}

	// L'écriture telle qu'enregistrée, pour la comparer au calcul à partir de la facture actuelle.
	CalculEcriture.Resultat enValeur() {
		return new CalculEcriture.Resultat(dateEcriture, numeroPiece, libelle,
				lignes.stream().map(LigneEcriture::enValeur).toList());
	}

	// US-47 : corrigée à la main depuis sa génération.
	public boolean isModifiee() {
		return modifieeLe != null;
	}

	public boolean isExportee() {
		return exporteeLe != null;
	}

	public Long getId() {
		return id;
	}

	public Long getDossierId() {
		return dossierId;
	}

	public Long getFactureId() {
		return factureId;
	}

	public Journal getJournal() {
		return journal;
	}

	public LocalDate getDateEcriture() {
		return dateEcriture;
	}

	public String getLibelle() {
		return libelle;
	}

	public Instant getGenereeLe() {
		return genereeLe;
	}

	public Compte getGenereePar() {
		return genereePar;
	}

	public Instant getExporteeLe() {
		return exporteeLe;
	}

	public String getNumeroPiece() {
		return numeroPiece;
	}

	public Instant getModifieeLe() {
		return modifieeLe;
	}

	public Compte getModifieePar() {
		return modifieePar;
	}

	public List<LigneEcriture> getLignes() {
		return List.copyOf(lignes);
	}

}
