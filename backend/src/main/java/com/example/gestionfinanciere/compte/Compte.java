package com.example.gestionfinanciere.compte;

import java.time.Duration;
import java.time.Instant;

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

	@Column(name = "nom_affiche", nullable = false)
	private String nomAffiche;

	@Column(name = "telephone", nullable = false)
	private String telephone;

	// US-05 : mauvais mots de passe de suite, et fin du blocage éventuel.
	@Column(name = "echecs_consecutifs", nullable = false)
	private int echecsConsecutifs;

	@Column(name = "bloque_jusqu_a")
	private Instant bloqueJusqua;

	protected Compte() {
	}

	public Compte(String email, String motDePasseHash, Role role, boolean actif, String nomAffiche, String telephone) {
		this.email = email;
		this.motDePasseHash = motDePasseHash;
		this.role = role;
		this.actif = actif;
		this.nomAffiche = nomAffiche;
		this.telephone = telephone;
	}

	public boolean estBloque(Instant maintenant) {
		return bloqueJusqua != null && maintenant.isBefore(bloqueJusqua);
	}

	/**
	 * US-05 RG1 : compte un mauvais mot de passe ; au {@code seuil}-ième de suite, bloque
	 * le compte pendant {@code duree}. Renvoie vrai si ce mauvais mot de passe le bloque.
	 */
	public boolean enregistrerEchec(Instant maintenant, int seuil, Duration duree) {
		echecsConsecutifs++;
		if (echecsConsecutifs < seuil) {
			return false;
		}
		bloqueJusqua = maintenant.plus(duree);
		echecsConsecutifs = 0;
		return true;
	}

	// US-05 RG3 : une connexion réussie remet le compteur à zéro.
	public void reinitialiserEchecs() {
		echecsConsecutifs = 0;
		bloqueJusqua = null;
	}

	// US-06 : nouveau mot de passe ; le compte est aussi débloqué.
	public void changerMotDePasse(String nouvelleEmpreinte) {
		motDePasseHash = nouvelleEmpreinte;
		reinitialiserEchecs();
	}

	// US-04 RG1 : numéro masqué, ex. « 06 •• •• •• 42 ».
	public String telephoneMasque() {
		String chiffres = telephone == null ? "" : telephone.replaceAll("\\D", "");
		if (chiffres.length() < 4) {
			return "•• •• •• •• ••";
		}
		return chiffres.substring(0, 2) + " •• •• •• " + chiffres.substring(chiffres.length() - 2);
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

	public String getNomAffiche() {
		return nomAffiche;
	}

	public String getTelephone() {
		return telephone;
	}

	public int getEchecsConsecutifs() {
		return echecsConsecutifs;
	}

	public Instant getBloqueJusqua() {
		return bloqueJusqua;
	}

}
