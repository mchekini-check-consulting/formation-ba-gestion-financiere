package com.example.gestionfinanciere.compte;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "compte")
public class Compte {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "email", nullable = false)
	private String email;

	@Column(name = "mot_de_passe_hash", nullable = false)
	private String motDePasseHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false)
	private Role role;

	@Column(name = "actif", nullable = false)
	private boolean actif;

	protected Compte() {
	}

	public Compte(String email, String motDePasseHash, Role role, boolean actif) {
		this.email = email;
		this.motDePasseHash = motDePasseHash;
		this.role = role;
		this.actif = actif;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getMotDePasseHash() {
		return motDePasseHash;
	}

	public Role getRole() {
		return role;
	}

	public boolean isActif() {
		return actif;
	}

}
