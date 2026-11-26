package com.example.gestionfinanciere.ecriture;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// US-56 Ligne d'une écriture (V27__ecriture.sql) : un compte du plan du dossier, au débit ou au crédit.
@Entity
@Table(name = "ligne_ecriture")
public class LigneEcriture {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "ecriture_id", nullable = false)
	private Ecriture ecriture;

	@Column(name = "ordre", nullable = false)
	private int ordre;

	@Column(name = "numero_compte", nullable = false)
	private String numeroCompte;

	@Column(name = "libelle", nullable = false)
	private String libelle;

	@Column(name = "debit", nullable = false, precision = 12, scale = 2)
	private BigDecimal debit;

	@Column(name = "credit", nullable = false, precision = 12, scale = 2)
	private BigDecimal credit;

	protected LigneEcriture() {
	}

	LigneEcriture(Ecriture ecriture, int ordre, CalculEcriture.Ligne ligne) {
		this.ecriture = ecriture;
		this.ordre = ordre;
		this.numeroCompte = ligne.numeroCompte();
		this.libelle = ligne.libelle();
		this.debit = ligne.debit();
		this.credit = ligne.credit();
	}

	CalculEcriture.Ligne enValeur() {
		return new CalculEcriture.Ligne(numeroCompte, libelle, debit, credit);
	}

	public int getOrdre() {
		return ordre;
	}

	public String getNumeroCompte() {
		return numeroCompte;
	}

	public String getLibelle() {
		return libelle;
	}

	public BigDecimal getDebit() {
		return debit;
	}

	public BigDecimal getCredit() {
		return credit;
	}

}
