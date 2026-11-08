package com.example.gestionfinanciere.cabinet;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// US-36 RG3 : inscription en attente du code reçu par email qui confirme l'email. Le cabinet et son
// compte ne sont créés qu'avec le bon code. Seules les empreintes des secrets sont stockées.
@Entity
@Table(name = "inscription")
public class Inscription {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "nom_cabinet", nullable = false)
	private String nomCabinet;

	@Column(name = "siren", nullable = false)
	private String siren;

	@Column(name = "prenom", nullable = false)
	private String prenom;

	@Column(name = "nom", nullable = false)
	private String nom;

	@Column(name = "email", nullable = false)
	private String email;

	@Column(name = "mot_de_passe_hash", nullable = false)
	private String motDePasseHash;

	@Column(name = "nombre_dossiers_prevus", nullable = false)
	private int nombreDossiersPrevus;

	@Column(name = "code_parrainage_saisi")
	private String codeParrainageSaisi;

	@Column(name = "cgu_acceptees_le", nullable = false)
	private Instant cguAccepteesLe;

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

	@Column(name = "terminee", nullable = false)
	private boolean terminee;

	protected Inscription() {
	}

	public Inscription(FormulaireValide formulaire, String motDePasseHash, Instant cguAccepteesLe,
			String empreinteDefi, String empreinteCode, Instant envoyeLe, Instant expireLe) {
		this.nomCabinet = formulaire.nomCabinet();
		this.siren = formulaire.siren();
		this.prenom = formulaire.prenom();
		this.nom = formulaire.nom();
		this.email = formulaire.email();
		this.nombreDossiersPrevus = formulaire.nombreDossiers();
		this.codeParrainageSaisi = formulaire.codeParrainage();
		this.motDePasseHash = motDePasseHash;
		this.cguAccepteesLe = cguAccepteesLe;
		this.empreinteDefi = empreinteDefi;
		this.empreinteCode = empreinteCode;
		this.envoyeLe = envoyeLe;
		this.expireLe = expireLe;
	}

	public boolean estExpiree(Instant maintenant) {
		return !maintenant.isBefore(expireLe);
	}

	public int enregistrerCodeFaux() {
		return ++codesFaux;
	}

	public void terminer() {
		terminee = true;
	}

	// Un nouveau code remplace l'ancien (même règle que US-04 RG2).
	public void remplacerCode(String nouvelleEmpreinte, Instant envoi, Instant expiration) {
		empreinteCode = nouvelleEmpreinte;
		envoyeLe = envoi;
		expireLe = expiration;
	}

	public String nomAffiche() {
		return prenom + " " + nom;
	}

	public Long getId() {
		return id;
	}

	public String getNomCabinet() {
		return nomCabinet;
	}

	public String getSiren() {
		return siren;
	}

	public String getPrenom() {
		return prenom;
	}

	public String getNom() {
		return nom;
	}

	public String getEmail() {
		return email;
	}

	public String getMotDePasseHash() {
		return motDePasseHash;
	}

	public int getNombreDossiersPrevus() {
		return nombreDossiersPrevus;
	}

	public String getCodeParrainageSaisi() {
		return codeParrainageSaisi;
	}

	public Instant getCguAccepteesLe() {
		return cguAccepteesLe;
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

	public boolean isTerminee() {
		return terminee;
	}

}
