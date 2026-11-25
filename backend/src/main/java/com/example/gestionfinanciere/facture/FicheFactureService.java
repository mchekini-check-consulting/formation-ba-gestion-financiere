package com.example.gestionfinanciere.facture;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.dossier.AccesDossier;
import com.example.gestionfinanciere.dossier.DossierRepository;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.Loggable;
import com.example.gestionfinanciere.plancomptable.CompteComptable;
import com.example.gestionfinanciere.plancomptable.CompteComptableRepository;

/**
 * US-55 Vérifier, corriger et valider les informations d'une facture. L'appelant vérifie avant que
 * la personne connectée est un collaborateur d'un cabinet ; ici, le dossier doit être accessible
 * (US-11 RG3, US-20). Jamais les valeurs des champs dans les logs (noms de tiers, montants).
 */
@Service
@Loggable
public class FicheFactureService {

	// RG3.
	static final String MESSAGE_COMPTE_ABSENT = "Le compte %s n'est pas dans le plan comptable du dossier";
	static final String MESSAGE_COMPTE_DESACTIVE = "Le compte %s est désactivé dans le plan comptable du dossier";

	private static final String CHAMP_DOSSIER = "dossierId";
	private static final String CHAMP_FACTURE = "factureId";

	private final FactureRepository factures;
	private final ChampLuRepository champsLus;
	private final ModificationFactureRepository modifications;
	private final ChangementStatutFactureRepository historique;
	private final CompteComptableRepository comptes;
	private final DossierRepository dossiers;
	private final Clock horloge;

	public FicheFactureService(FactureRepository factures, ChampLuRepository champsLus,
			ModificationFactureRepository modifications, ChangementStatutFactureRepository historique,
			CompteComptableRepository comptes, DossierRepository dossiers, Clock horloge) {
		this.factures = factures;
		this.champsLus = champsLus;
		this.modifications = modifications;
		this.historique = historique;
		this.comptes = comptes;
		this.dossiers = dossiers;
		this.horloge = horloge;
	}

	/**
	 * RG2 et RG7 : enregistre les champs corrigés ou saisis, sans valider ; les champs vides restent
	 * permis. RG3 : un nouveau compte de contrepartie doit être dans le plan du dossier. RG8 : chaque
	 * champ changé garde l'ancienne et la nouvelle valeur, l'auteur et la date.
	 */
	@Transactional
	public ResultatFiche enregistrer(Compte collaborateur, Long dossierId, Long factureId, InformationsFacture demande) {
		ResultatFiche resultat = enregistrerSansJournal(collaborateur, dossierId, factureId, demande);
		journal("facture.correction", resultat, collaborateur, dossierId, factureId);
		return resultat;
	}

	private ResultatFiche enregistrerSansJournal(Compte collaborateur, Long dossierId, Long factureId,
			InformationsFacture demande) {
		Optional<Facture> trouvee = factureAccessible(collaborateur, dossierId, factureId);
		if (trouvee.isEmpty()) {
			return ResultatFiche.refuse(ResultatFiche.Issue.INTROUVABLE);
		}
		Facture facture = trouvee.get();
		if (!facture.isModifiable()) {
			return ResultatFiche.refuse(ResultatFiche.Issue.NON_MODIFIABLE);
		}
		InformationsFacture infos = demande.nettoyee();
		InformationsFacture actuelles = InformationsFacture.de(facture);
		Optional<String> refus = ControleFiche.format(infos);
		if (refus.isEmpty() && infos.compteContrepartie() != null
				&& !infos.compteContrepartie().equals(actuelles.compteContrepartie())) {
			refus = contrepartie(facture, infos.compteContrepartie());
		}
		if (refus.isPresent()) {
			return ResultatFiche.refuse(refus.get());
		}
		List<ChampLu> lus = champsLus.findByFactureId(factureId);
		appliquer(collaborateur, facture, actuelles, infos, lus);
		facture.mettreAJourAVerifier(resteAVerifier(facture, lus));
		return new ResultatFiche(ResultatFiche.Issue.ENREGISTREE, null, VueFacture.de(facture, lus), null);
	}

	/**
	 * RG3 à RG6 : enregistre les champs puis valide la facture : champs obligatoires (RG5, le premier
	 * manquant est nommé), HT + TVA = TTC (RG4), contrepartie dans le plan du dossier (RG3). Refusée :
	 * rien n'est enregistré et le statut ne change pas. Validée : statut « Traitée » (une facture Reçue
	 * ou À compléter passe d'abord par « En cours de traitement », US-51 RG2), avec la prochaine facture
	 * à traiter du même onglet. CA4 : une facture saisie à la main se valide comme une facture lue.
	 */
	@Transactional
	public ResultatFiche valider(Compte collaborateur, Long dossierId, Long factureId, InformationsFacture demande) {
		ResultatFiche resultat = validerSansJournal(collaborateur, dossierId, factureId, demande);
		journal("facture.validation", resultat, collaborateur, dossierId, factureId);
		return resultat;
	}

	private ResultatFiche validerSansJournal(Compte collaborateur, Long dossierId, Long factureId,
			InformationsFacture demande) {
		Optional<Facture> trouvee = factureAccessible(collaborateur, dossierId, factureId);
		if (trouvee.isEmpty()) {
			return ResultatFiche.refuse(ResultatFiche.Issue.INTROUVABLE);
		}
		Facture facture = trouvee.get();
		if (!facture.isModifiable()) {
			return ResultatFiche.refuse(ResultatFiche.Issue.NON_MODIFIABLE);
		}
		InformationsFacture infos = demande.nettoyee();
		Optional<String> refus = ControleFiche.format(infos).or(() -> ControleFiche.obligatoires(infos))
				.or(() -> ControleFiche.coherence(infos)).or(() -> contrepartie(facture, infos.compteContrepartie()));
		if (refus.isPresent()) {
			return ResultatFiche.refuse(refus.get());
		}
		List<ChampLu> lus = champsLus.findByFactureId(factureId);
		appliquer(collaborateur, facture, InformationsFacture.de(facture), infos, lus);
		// Le comptable a vérifié toute la fiche : plus aucun champ « À vérifier ».
		lus.forEach(ChampLu::marquerVerifie);
		facture.mettreAJourAVerifier(false);
		if (facture.getStatut() != Facture.Statut.EN_COURS) {
			passer(collaborateur, facture, Facture.Statut.EN_COURS);
		}
		passer(collaborateur, facture, Facture.Statut.TRAITEE);
		return new ResultatFiche(ResultatFiche.Issue.VALIDEE, null, VueFacture.de(facture, lus), suivante(facture));
	}

	// RG8 : chaque champ changé est gardé ; un champ corrigé n'est plus « À vérifier » (RG2).
	private void appliquer(Compte collaborateur, Facture facture, InformationsFacture actuelles,
			InformationsFacture infos, List<ChampLu> lus) {
		Instant maintenant = horloge.instant();
		List<InformationsFacture.Changement> changements = actuelles.changementsVers(infos);
		changements.forEach(changement -> modifications
				.save(new ModificationFacture(facture, changement, collaborateur, maintenant)));
		lus.stream().filter(lu -> changements.stream().anyMatch(c -> c.champ() == lu.getChamp()))
				.forEach(ChampLu::marquerVerifie);
		facture.corriger(infos);
	}

	// US-51 RG4 : chaque changement de statut garde son auteur et sa date.
	private void passer(Compte collaborateur, Facture facture, Facture.Statut nouveau) {
		Facture.Statut ancien = facture.getStatut();
		facture.changerStatut(nouveau, null);
		historique.save(new ChangementStatutFacture(facture, ancien, nouveau, null, collaborateur, horloge.instant()));
	}

	// RG3 : compte du plan comptable du dossier, actif (US-41 RG3 : un compte désactivé ne se choisit plus).
	private Optional<String> contrepartie(Facture facture, String numero) {
		Optional<CompteComptable> compte = comptes.findByDossierIdAndNumero(facture.getDossier().getId(), numero);
		if (compte.isEmpty()) {
			return Optional.of(MESSAGE_COMPTE_ABSENT.formatted(numero));
		}
		if (!compte.get().isActif()) {
			return Optional.of(MESSAGE_COMPTE_DESACTIVE.formatted(numero));
		}
		return Optional.empty();
	}

	// US-53 : pastille « À vérifier » tant qu'un champ lu est douteux ou qu'un champ est vide.
	private static boolean resteAVerifier(Facture facture, List<ChampLu> lus) {
		InformationsFacture infos = InformationsFacture.de(facture);
		Object taux = facture.isTauxTvaMultiple() ? "Multiple" : infos.tauxTva();
		boolean champVide = Stream.of(infos.typeTiers(), infos.nomTiers(), infos.numeroFacture(),
				infos.dateEmission(), infos.montantHt(), infos.montantTva(), taux, infos.montantTtc(),
				infos.compteContrepartie()).anyMatch(valeur -> valeur == null);
		return champVide || lus.stream().anyMatch(ChampLu::isAVerifier);
	}

	// RG6 : prochaine facture à traiter (Reçue ou En cours) du même onglet, dans l'ordre de la liste.
	private Long suivante(Facture facture) {
		return factures
				.findByDossierIdAndSensOrderByDeposeeLeDescIdDesc(facture.getDossier().getId(), facture.getSens())
				.stream().filter(autre -> !autre.getId().equals(facture.getId()))
				.filter(autre -> autre.getStatut() == Facture.Statut.RECUE
						|| autre.getStatut() == Facture.Statut.EN_COURS)
				.map(Facture::getId).findFirst().orElse(null);
	}

	private Optional<Facture> factureAccessible(Compte collaborateur, Long dossierId, Long factureId) {
		return AccesDossier.dossierAccessible(dossiers, collaborateur, dossierId)
				.flatMap(dossier -> factures.findByIdAndDossierId(factureId, dossier.getId()));
	}

	// Issue seulement (jamais le message de refus, qui peut contenir un numéro de compte saisi).
	private static void journal(String event, ResultatFiche resultat, Compte collaborateur, Long dossierId,
			Long factureId) {
		EvenementMetier.de(event, resultat.issue()).avec(collaborateur.identifiantsPourLogs())
				.avec(CHAMP_DOSSIER, dossierId).avec(CHAMP_FACTURE, factureId)
				.journaliser(resultat.issue() == ResultatFiche.Issue.ENREGISTREE
						|| resultat.issue() == ResultatFiche.Issue.VALIDEE);
	}

}
