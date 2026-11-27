package com.example.gestionfinanciere.banque;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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

// US-30 RG1 Opération bancaire validée (V29__operation_validee.sql) : l'opération telle qu'elle était à
// la validation, sa catégorie, son compte et son taux de TVA. Son écriture est au journal de banque.
@Entity
@Table(name = "operation_validee")
public class OperationValidee {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "dossier_id", nullable = false)
	private Long dossierId;

	@Column(name = "reference", nullable = false)
	private String reference;

	@Column(name = "date_operation", nullable = false)
	private LocalDate dateOperation;

	@Column(name = "libelle", nullable = false)
	private String libelle;

	@Column(name = "tiers")
	private String tiers;

	@Column(name = "montant", nullable = false)
	private BigDecimal montant;

	@Enumerated(EnumType.STRING)
	@Column(name = "sens", nullable = false)
	private Transaction.Sens sens;

	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false)
	private Transaction.Source source;

	@Column(name = "categorie", nullable = false)
	private String categorie;

	@Column(name = "numero_compte", nullable = false)
	private String numeroCompte;

	@Column(name = "taux_tva", nullable = false)
	private BigDecimal tauxTva;

	@Column(name = "validee_le", nullable = false)
	private Instant valideeLe;

	@ManyToOne(optional = false)
	@JoinColumn(name = "validee_par_id", nullable = false)
	private Compte valideePar;

	protected OperationValidee() {
	}

	public OperationValidee(Long dossierId, Transaction transaction, String categorie, String numeroCompte,
			BigDecimal tauxTva, Compte valideePar, Instant valideeLe) {
		this.dossierId = dossierId;
		this.reference = transaction.reference();
		this.dateOperation = transaction.date();
		this.libelle = transaction.libelle();
		this.tiers = transaction.tiers();
		this.montant = transaction.montant();
		this.sens = transaction.sens();
		this.source = transaction.source();
		this.categorie = categorie;
		this.numeroCompte = numeroCompte;
		this.tauxTva = tauxTva;
		this.valideePar = valideePar;
		this.valideeLe = valideeLe;
	}

	public Long getId() {
		return id;
	}

	public Long getDossierId() {
		return dossierId;
	}

	public String getReference() {
		return reference;
	}

	public LocalDate getDateOperation() {
		return dateOperation;
	}

	public String getLibelle() {
		return libelle;
	}

	public String getTiers() {
		return tiers;
	}

	public BigDecimal getMontant() {
		return montant;
	}

	public Transaction.Sens getSens() {
		return sens;
	}

	public Transaction.Source getSource() {
		return source;
	}

	public String getCategorie() {
		return categorie;
	}

	public String getNumeroCompte() {
		return numeroCompte;
	}

	public BigDecimal getTauxTva() {
		return tauxTva;
	}

	public Instant getValideeLe() {
		return valideeLe;
	}

	public Compte getValideePar() {
		return valideePar;
	}

}
