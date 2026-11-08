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

	// US-05 : mauvais mots de passe de suite, et fin du blocage éventuel.
	@Column(name = "echecs_consecutifs", nullable = false)
	private int echecsConsecutifs;

	@Column(name = "bloque_jusqu_a")
	private Instant bloqueJusqua;

	// US-36 : cabinet du collaborateur (vide pour les comptes de démonstration et les dirigeants).
	@ManyToOne
	@JoinColumn(name = "cabinet_id")
	private Cabinet cabinet;

	// US-19 RG2 : rôle dans le cabinet (vide sans cabinet). US-36 RG3 : la personne qui crée
	// le compte du cabinet en est administratrice.
	@Enumerated(EnumType.STRING)
	@Column(name = "role_cabinet")
	private RoleCabinet roleCabinet;

	// Espace client (US-09, US-14) : dossier du dirigeant (vide pour les collaborateurs).
	// Simple numéro : le paquet dossier dépend déjà de compte.
	@Column(name = "dossier_id")
	private Long dossierId;

	protected Compte() {
	}

	public Compte(String email, String motDePasseHash, Role role, boolean actif, String nomAffiche) {
		this.email = email;
		this.motDePasseHash = motDePasseHash;
		this.role = role;
		this.actif = actif;
		this.nomAffiche = nomAffiche;
	}

	// US-36 RG3 : compte de la personne qui crée le cabinet, administratrice de celui-ci.
	public static Compte administrateurDe(Cabinet cabinet, String email, String motDePasseHash, String nomAffiche) {
		Compte compte = new Compte(email, motDePasseHash, Role.COLLABORATEUR, true, nomAffiche);
		compte.cabinet = cabinet;
		compte.roleCabinet = RoleCabinet.ADMINISTRATEUR;
		return compte;
	}

	/**
	 * US-19 : collaborateur invité dans un cabinet. Son compte reste inactif (« Invitée ») tant
	 * qu'il ne l'a pas activé depuis le lien reçu par email (US-08), où il choisira son mot de
	 * passe : en attendant, l'empreinte reçue ne correspond à aucun mot de passe connu.
	 */
	public static Compte invite(Cabinet cabinet, String email, String empreinteInutilisable, String nomAffiche,
			RoleCabinet roleCabinet) {
		Compte compte = new Compte(email, empreinteInutilisable, Role.COLLABORATEUR, false, nomAffiche);
		compte.cabinet = cabinet;
		compte.roleCabinet = roleCabinet;
		return compte;
	}

	/**
	 * Dirigeant invité dans l'espace client de son dossier (US-14) : compte inactif (« Invité »)
	 * jusqu'à son activation depuis le lien reçu par email (US-08).
	 */
	public static Compte dirigeantInvite(Long dossierId, String email, String empreinteInutilisable,
			String nomAffiche) {
		Compte compte = new Compte(email, empreinteInutilisable, Role.DIRIGEANT, false, nomAffiche);
		compte.dossierId = dossierId;
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

	// US-04 : email masqué sur l'écran du code, ex. « j•••••@cabinet-test.fr ».
	public String emailMasque() {
		return masquerEmail(email);
	}

	// Première lettre + « ••••• » + @domaine.
	public static String masquerEmail(String email) {
		int arobase = email == null ? -1 : email.lastIndexOf('@');
		if (arobase < 1) {
			return "•••••";
		}
		return email.charAt(0) + "•••••" + email.substring(arobase);
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

	public int getEchecsConsecutifs() {
		return echecsConsecutifs;
	}

	public Instant getBloqueJusqua() {
		return bloqueJusqua;
	}

	public Cabinet getCabinet() {
		return cabinet;
	}

	public RoleCabinet getRoleCabinet() {
		return roleCabinet;
	}

	public Long getDossierId() {
		return dossierId;
	}

	public boolean isAdministrateur() {
		return roleCabinet == RoleCabinet.ADMINISTRATEUR;
	}

}
