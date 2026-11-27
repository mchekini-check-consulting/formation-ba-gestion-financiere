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

// US-56 Écriture d'une facture d'achat validée (V27__ecriture.sql), au journal des achats ;
// US-57 d'une facture de vente validée, au journal des ventes.
// RG5 : re-générée (lignes remplacées) tant qu'elle n'est pas exportée. Supprimée avec sa facture.
@Entity
@Table(name = "ecriture")
public class Ecriture {

	// Journal des achats (US-56) ou des ventes (US-57, V28__ecriture_vente.sql).
	public enum Journal {
		AC, VT
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

	@OneToMany(mappedBy = "ecriture", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("ordre")
	private List<LigneEcriture> lignes = new ArrayList<>();

	protected Ecriture() {
	}

	// RG1 : écriture d'une facture d'achat, au journal des achats.
	public static Ecriture achat(Long dossierId, Long factureId) {
		return au(Journal.AC, dossierId, factureId);
	}

	// US-57 RG1 : écriture d'une facture de vente au journal des ventes (VT).
	public static Ecriture au(Journal journal, Long dossierId, Long factureId) {
		Ecriture ecriture = new Ecriture();
		ecriture.dossierId = dossierId;
		ecriture.factureId = factureId;
		ecriture.journal = journal;
		return ecriture;
	}

	// RG1, RG3, RG4 ; RG5 : les lignes précédentes sont remplacées.
	void remplir(CalculEcriture.Resultat calcul, Compte auteur, Instant quand) {
		dateEcriture = calcul.date();
		libelle = calcul.libelle();
		genereePar = auteur;
		genereeLe = quand;
		lignes.clear();
		List<CalculEcriture.Ligne> calculees = calcul.lignes();
		for (int ordre = 0; ordre < calculees.size(); ordre++) {
			lignes.add(new LigneEcriture(this, ordre + 1, calculees.get(ordre)));
		}
	}

	// L'écriture telle qu'enregistrée, pour la comparer au calcul à partir de la facture actuelle.
	CalculEcriture.Resultat enValeur() {
		return new CalculEcriture.Resultat(dateEcriture, libelle, lignes.stream().map(LigneEcriture::enValeur).toList());
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

	public List<LigneEcriture> getLignes() {
		return List.copyOf(lignes);
	}

}
