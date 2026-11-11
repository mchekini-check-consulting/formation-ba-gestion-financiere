package com.example.gestionfinanciere.dossier;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.example.gestionfinanciere.compte.Compte;

// US-20 : comptable qui suit un dossier (V15__attribution_dossier.sql).
@Entity
@Table(name = "dossier_attribution")
public class AttributionDossier {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "dossier_id", nullable = false)
	private Dossier dossier;

	@ManyToOne(optional = false)
	@JoinColumn(name = "comptable_id", nullable = false)
	private Compte comptable;

	@Column(name = "attribue_le", nullable = false)
	private Instant attribueLe;

	protected AttributionDossier() {
	}

	public AttributionDossier(Dossier dossier, Compte comptable, Instant attribueLe) {
		this.dossier = dossier;
		this.comptable = comptable;
		this.attribueLe = attribueLe;
	}

	public Long getId() {
		return id;
	}

	public Dossier getDossier() {
		return dossier;
	}

	public Compte getComptable() {
		return comptable;
	}

	public Instant getAttribueLe() {
		return attribueLe;
	}

}
