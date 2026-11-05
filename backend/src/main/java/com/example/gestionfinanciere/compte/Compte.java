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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.example.gestionfinanciere.cabinet.Cabinet;

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

	// US-36 : cabinet du collaborateur (vide pour les comptes de démonstration et les dirigeants).
	@ManyToOne
	@JoinColumn(name = "cabinet_id")
	private Cabinet cabinet;

	// US-36 RG3 : la personne qui crée le compte du cabinet en est administratrice.
	@Column(name = "administrateur", nullable = false)
	private boolean administrateur;

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

	// US-36 RG3 : compte de la personne qui crée le cabinet, administratrice de celui-ci.
	public static Compte administrateurDe(Cabinet cabinet, String email, String motDePasseHash, String nomAffiche,
			String telephone) {
		Compte compte = new Compte(email, motDePasseHash, Role.COLLABORATEUR, true, nomAffiche, telephone);
		compte.cabinet = cabinet;
		compte.administrateur = true;
		return compte;
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
		return masquer(telephone);
	}

	// Aussi utilisé pour le mobile d'une inscription (US-36), avant la création du compte.
	public static String masquer(String telephone) {
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

	public Cabinet getCabinet() {
		return cabinet;
	}

	public boolean isAdministrateur() {
		return administrateur;
	}

}
