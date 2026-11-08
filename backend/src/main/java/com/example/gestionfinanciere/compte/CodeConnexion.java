package com.example.gestionfinanciere.compte;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// US-04 : code reçu par email lors d'une tentative de connexion. Le "défi" identifie la tentative
// (donné à l'écran après le mot de passe) ; seules les empreintes sont stockées.
// La table garde son nom d'origine (code_sms, migration V7).
@Entity
@Table(name = "code_sms")
public class CodeConnexion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "compte_id", nullable = false)
	private Compte compte;

	@Column(name = "empreinte_defi", nullable = false)
	private String empreinteDefi;

	@Column(name = "empreinte_code", nullable = false)
	private String empreinteCode;

	@Column(name = "envoye_le", nullable = false)
	private Instant envoyeLe;

	@Column(name = "expire_le", nullable = false)
	private Instant expireLe;

	@Column(name = "codes_faux", nullable = false)
	private int codesFaux;

	@Column(name = "termine", nullable = false)
	private boolean termine;

	protected CodeConnexion() {
	}

	public CodeConnexion(Compte compte, String empreinteDefi, String empreinteCode, Instant envoyeLe, Instant expireLe) {
		this.compte = compte;
		this.empreinteDefi = empreinteDefi;
		this.empreinteCode = empreinteCode;
		this.envoyeLe = envoyeLe;
		this.expireLe = expireLe;
	}

	public boolean estExpire(Instant maintenant) {
		return !maintenant.isBefore(expireLe);
	}

	public int enregistrerCodeFaux() {
		return ++codesFaux;
	}

	public void terminer() {
		termine = true;
	}

	// US-04 RG2 : le nouveau code remplace l'ancien.
	public void remplacerCode(String nouvelleEmpreinte, Instant envoi, Instant expiration) {
		empreinteCode = nouvelleEmpreinte;
		envoyeLe = envoi;
		expireLe = expiration;
	}

	public Long getId() {
		return id;
	}

	public Compte getCompte() {
		return compte;
	}

	public String getEmpreinteDefi() {
		return empreinteDefi;
	}

	public String getEmpreinteCode() {
		return empreinteCode;
	}

	public Instant getEnvoyeLe() {
		return envoyeLe;
	}

	public Instant getExpireLe() {
		return expireLe;
	}

	public int getCodesFaux() {
		return codesFaux;
	}

	public boolean isTermine() {
		return termine;
	}

}
