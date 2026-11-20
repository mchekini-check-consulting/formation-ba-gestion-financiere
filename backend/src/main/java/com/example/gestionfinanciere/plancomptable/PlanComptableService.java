package com.example.gestionfinanciere.plancomptable;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.dossier.AccesDossier;
import com.example.gestionfinanciere.dossier.DossierRepository;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.Loggable;
import com.example.gestionfinanciere.plancomptable.ResultatCompte.Statut;

/**
 * US-41 Plan comptable d'un dossier. L'appelant vérifie avant que la personne connectée est un
 * collaborateur d'un cabinet ; ici, mêmes droits que le dossier (US-11 RG3, US-20).
 * RG1 : le plan général est copié à la création du dossier ; la comptable ajoute des sous-comptes.
 * RG2 : chaque catégorie a son compte par défaut ; la banque utilise le 512.
 * RG3 : un compte déjà utilisé dans une écriture ne se supprime pas, il se désactive. Les écritures
 * arrivent avec l'US-30 : en attendant, les comptes du plan général ne se suppriment jamais et un
 * sous-compte se supprime librement.
 */
@Service
@Loggable
public class PlanComptableService {

	static final String CODE_BANQUE = "BANQUE";
	static final String LIBELLE_BANQUE = "Banque";
	static final String NUMERO_BANQUE = "512";
	private static final String DOSSIER = "dossierId";

	private final DossierRepository dossiers;
	private final CompteComptableRepository comptes;
	private final CategorieRepository categories;
	private final Clock horloge;

	public PlanComptableService(DossierRepository dossiers, CompteComptableRepository comptes,
			CategorieRepository categories, Clock horloge) {
		this.dossiers = dossiers;
		this.comptes = comptes;
		this.categories = categories;
		this.horloge = horloge;
	}

	// Comptes du dossier triés par numéro, et comptes par défaut ; vide si le dossier n'est pas accessible.
	@Transactional(readOnly = true)
	public Optional<PlanComptable> consulter(Compte collaborateur, Long dossierId) {
		if (!accessible(collaborateur, dossierId)) {
			journal(EvenementMetier.de("plan_comptable.consultation", "introuvable"), collaborateur, dossierId)
					.warn();
			return Optional.empty();
		}
		List<VueCompte> plan = comptes.findByDossierIdOrderByNumero(dossierId).stream().map(VueCompte::de).toList();
		journal(EvenementMetier.de("plan_comptable.consultation", "consulte"), collaborateur, dossierId)
				.avec("nombre", plan.size()).info();
		return Optional.of(new PlanComptable(plan, parDefaut()));
	}

	// RG1, CA1 : nouveau sous-compte (ex. 401MOULINS « Moulins Bertrand »). CA2 : numéro invalide.
	@Transactional
	public ResultatCompte creer(Compte collaborateur, Long dossierId, DemandeCompte demande) {
		ResultatCompte resultat = creerSansJournal(collaborateur, dossierId, demande);
		journal(EvenementMetier.de("plan_comptable.creation", resultat.statut()),
				collaborateur, dossierId).avecChampsInvalides(resultat.erreurs())
				.journaliser(resultat.statut() == Statut.CREE);
		return resultat;
	}

	private ResultatCompte creerSansJournal(Compte collaborateur, Long dossierId, DemandeCompte demande) {
		if (!accessible(collaborateur, dossierId)) {
			return ResultatCompte.de(Statut.DOSSIER_INTROUVABLE);
		}
		List<CompteComptable> plan = comptes.findByDossierIdOrderByNumero(dossierId);
		List<String> generaux = plan.stream().filter(compte -> !compte.isSousCompte()).map(CompteComptable::getNumero)
				.toList();
		Map<String, String> erreurs = ValidationCompte.erreurs(demande, generaux);
		if (!erreurs.isEmpty()) {
			return ResultatCompte.invalide(erreurs);
		}
		String numero = ValidationCompte.numero(demande.numero());
		if (comptes.existsByDossierIdAndNumero(dossierId, numero)) {
			return ResultatCompte.de(Statut.DEJA_EXISTANT);
		}
		CompteComptable cree = comptes.save(CompteComptable.sousCompte(dossierId, numero,
				ValidationCompte.libelle(demande.libelle()), horloge.instant()));
		return ResultatCompte.de(Statut.CREE, VueCompte.de(cree));
	}

	// RG3 : désactiver (le compte ne se choisit plus dans une écriture) ou réactiver un compte.
	@Transactional
	public ResultatCompte changerActivation(Compte collaborateur, Long dossierId, String numero,
			DemandeActivation demande) {
		ResultatCompte resultat;
		if (demande == null || demande.actif() == null) {
			resultat = ResultatCompte.invalide(Map.of("actif", "Indiquez si le compte est actif"));
		} else {
			resultat = trouver(collaborateur, dossierId, numero).map(compte -> {
				compte.setActif(demande.actif());
				return ResultatCompte.de(Statut.MODIFIE, VueCompte.de(compte));
			}).orElseGet(() -> absent(collaborateur, dossierId));
		}
		journal(EvenementMetier.de("plan_comptable.activation", resultat.statut()),
				collaborateur, dossierId).avec("actif", demande == null ? null : demande.actif())
				.journaliser(resultat.statut() == Statut.MODIFIE);
		return resultat;
	}

	// RG3 : seuls les sous-comptes se suppriment ; un compte du plan général se désactive.
	@Transactional
	public ResultatCompte supprimer(Compte collaborateur, Long dossierId, String numero) {
		ResultatCompte resultat = trouver(collaborateur, dossierId, numero).map(compte -> {
			if (!compte.isSousCompte()) {
				return ResultatCompte.de(Statut.COMPTE_GENERAL);
			}
			comptes.delete(compte);
			return ResultatCompte.de(Statut.SUPPRIME);
		}).orElseGet(() -> absent(collaborateur, dossierId));
		journal(EvenementMetier.de("plan_comptable.suppression", resultat.statut()),
				collaborateur, dossierId).journaliser(resultat.statut() == Statut.SUPPRIME);
		return resultat;
	}

	// RG2 : comptes par défaut des catégories, puis celui de la banque.
	private List<PlanComptable.CompteParDefaut> parDefaut() {
		List<PlanComptable.CompteParDefaut> liste = new ArrayList<>();
		for (Categorie categorie : categories.findAllByOrderByOrdre()) {
			liste.add(new PlanComptable.CompteParDefaut(categorie.getCode(), categorie.getLibelle(),
					categorie.getNumeroCompte()));
		}
		liste.add(new PlanComptable.CompteParDefaut(CODE_BANQUE, LIBELLE_BANQUE, NUMERO_BANQUE));
		return liste;
	}

	private Optional<CompteComptable> trouver(Compte collaborateur, Long dossierId, String numero) {
		if (!accessible(collaborateur, dossierId)) {
			return Optional.empty();
		}
		return comptes.findByDossierIdAndNumero(dossierId, ValidationCompte.numero(numero));
	}

	private ResultatCompte absent(Compte collaborateur, Long dossierId) {
		return ResultatCompte.de(accessible(collaborateur, dossierId) ? Statut.COMPTE_INTROUVABLE
				: Statut.DOSSIER_INTROUVABLE);
	}

	private boolean accessible(Compte collaborateur, Long dossierId) {
		return AccesDossier.dossierAccessible(dossiers, collaborateur, dossierId).isPresent();
	}

	// Identifiants techniques seulement : jamais le libellé d'un compte (souvent un nom de tiers).
	private static EvenementMetier journal(EvenementMetier evenement, Compte collaborateur, Long dossierId) {
		return evenement.avec(collaborateur.identifiantsPourLogs()).avec(DOSSIER, dossierId);
	}

}
