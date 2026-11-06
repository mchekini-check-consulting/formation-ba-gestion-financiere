package com.example.gestionfinanciere.equipe;

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

// US-19 : invitation d'un collaborateur, envoyée par email. Le lien sera utilisé pour activer
// le compte (US-08 RG1 : valable 7 jours, une seule fois). Seule l'empreinte du jeton est stockée.
@Entity
@Table(name = "invitation")
public class Invitation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "compte_id", nullable = false)
	private Compte compte;

	@ManyToOne(optional = false)
	@JoinColumn(name = "invitee_par_id", nullable = false)
	private Compte inviteePar;

	@Column(name = "empreinte_jeton", nullable = false)
	private String empreinteJeton;

	@Column(name = "envoyee_le", nullable = false)
	private Instant envoyeeLe;

	@Column(name = "expire_le", nullable = false)
	private Instant expireLe;

	@Column(name = "utilisee_le")
	private Instant utiliseeLe;

	protected Invitation() {
	}

	public Invitation(Compte compte, Compte inviteePar, String empreinteJeton, Instant envoyeeLe, Instant expireLe) {
		this.compte = compte;
		this.inviteePar = inviteePar;
		this.empreinteJeton = empreinteJeton;
		this.envoyeeLe = envoyeeLe;
		this.expireLe = expireLe;
	}

	public Long getId() {
		return id;
	}

	public Compte getCompte() {
		return compte;
	}

	public Compte getInviteePar() {
		return inviteePar;
	}

	public String getEmpreinteJeton() {
		return empreinteJeton;
	}

	public Instant getEnvoyeeLe() {
		return envoyeeLe;
	}

	public Instant getExpireLe() {
		return expireLe;
	}

	public Instant getUtiliseeLe() {
		return utiliseeLe;
	}

}
