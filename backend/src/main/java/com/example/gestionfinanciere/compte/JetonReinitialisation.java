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

// US-06 : lien « Mot de passe oublié » ; seule l'empreinte du jeton est stockée.
@Entity
@Table(name = "jeton_reinitialisation")
public class JetonReinitialisation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "compte_id", nullable = false)
	private Compte compte;

	@Column(name = "empreinte_jeton", nullable = false)
	private String empreinteJeton;

	@Column(name = "expire_le", nullable = false)
	private Instant expireLe;

	@Column(name = "utilise_le")
	private Instant utiliseLe;

	protected JetonReinitialisation() {
	}

	public JetonReinitialisation(Compte compte, String empreinteJeton, Instant expireLe) {
		this.compte = compte;
		this.empreinteJeton = empreinteJeton;
		this.expireLe = expireLe;
	}

	// RG1 : valable 1 heure et utilisable une seule fois.
	public boolean estValable(Instant maintenant) {
		return utiliseLe == null && maintenant.isBefore(expireLe);
	}

	public void utiliser(Instant maintenant) {
		utiliseLe = maintenant;
	}

	public Long getId() {
		return id;
	}

	public Compte getCompte() {
		return compte;
	}

	public String getEmpreinteJeton() {
		return empreinteJeton;
	}

	public Instant getExpireLe() {
		return expireLe;
	}

	public Instant getUtiliseLe() {
		return utiliseLe;
	}

}
