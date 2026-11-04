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

// US-07 : session ouverte après le code SMS ; seule l'empreinte du jeton est stockée.
@Entity
@Table(name = "session_utilisateur")
public class SessionUtilisateur {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "compte_id", nullable = false)
	private Compte compte;

	@Column(name = "empreinte_jeton", nullable = false)
	private String empreinteJeton;

	@Column(name = "derniere_activite", nullable = false)
	private Instant derniereActivite;

	@Column(name = "fermee", nullable = false)
	private boolean fermee;

	protected SessionUtilisateur() {
	}

	public SessionUtilisateur(Compte compte, String empreinteJeton, Instant derniereActivite) {
		this.compte = compte;
		this.empreinteJeton = empreinteJeton;
		this.derniereActivite = derniereActivite;
	}

	public void enregistrerActivite(Instant maintenant) {
		derniereActivite = maintenant;
	}

	public void fermer() {
		fermee = true;
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

	public Instant getDerniereActivite() {
		return derniereActivite;
	}

	public boolean isFermee() {
		return fermee;
	}

}
