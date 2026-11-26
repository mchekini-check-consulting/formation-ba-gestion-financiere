package com.example.gestionfinanciere.facture;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.dossier.AccesDossier;
import com.example.gestionfinanciere.dossier.DossierRepository;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.Loggable;
import com.example.gestionfinanciere.plancomptable.CompteComptable;
import com.example.gestionfinanciere.plancomptable.CompteComptableRepository;

/**
 * US-55 Vérifier, corriger et valider les informations d'une facture. Tous les champs se modifient
 * (RG2) ; la contrepartie est un compte actif du plan comptable du dossier (RG3) ; « Valider » exige
 * les champs obligatoires (RG5) et HT + TVA = TTC (RG4), puis passe la facture en « Traitée » (RG6).
 * Chaque modification garde son auteur et sa date (RG8). Une facture en cours de lecture ne se modifie pas.
 * US-56 : la validation d'une facture d'achat génère son écriture (RG1 à RG4). Une facture d'achat traitée
 * se corrige encore (informations complètes et cohérentes) tant que son écriture n'est pas exportée, puis
 * « Re-générer les écritures » la recalcule (RG5, CA2) ; exportée, plus rien ne change (CA3). Une facture
 * de vente traitée ne se modifie plus (écritures : US-57).
 */
@Service
@Loggable
public class CorrectionFactureService {

	static final String MESSAGE_TRAITEE = "Facture traitée : ses informations ne se modifient plus";
	static final String MESSAGE_EXPORTEE = "Écriture exportée : la facture ne se modifie plus";
	static final String MESSAGE_DEJA_VALIDEE =
			"Facture déjà validée : enregistrez la correction puis re-générez les écritures";
	static final String MESSAGE_NON_VALIDEE = "Validez la facture pour générer ses écritures";
	static final String MESSAGE_SANS_ECRITURE = "Pas d'écriture pour une facture de vente";
	static final String MESSAGE_LECTURE_EN_COURS = "Lecture en cours : réessayez dans quelques secondes";
	static final String MESSAGE_INCOHERENTS = "Montants incohérents : HT + TVA ≠ TTC";
	static final String MESSAGE_TYPE_TIERS = "Type de tiers : Fournisseur ou Client";
	static final String MESSAGE_TAUX = "Taux de TVA : entre 0 et 100 %";

	// RG5 : champs obligatoires pour valider, dans l'ordre de la fiche.
	static final Map<ChampFacture, String> OBLIGATOIRES = obligatoires();

	// Colonnes numeric(12, 2) et varchar de la table facture (V23).
	private static final BigDecimal MONTANT_MAX = new BigDecimal("10000000000");
	private static final BigDecimal TAUX_MAX = BigDecimal.valueOf(100);
	private static final int NOM_TIERS_MAX = 255;
	private static final int NUMERO_MAX = 100;
	private static final Set<String> TYPES_TIERS = Set.of("FOURNISSEUR", "CLIENT");
	private static final InformationsFacture VIDE = new InformationsFacture(null, null, null, null, null, null, null,
			null, null);

	private final FactureRepository factures;
	private final ChampLuRepository champsLus;
	private final ChangementStatutFactureRepository historique;
	private final CorrectionFactureRepository corrections;
	private final CompteComptableRepository comptes;
	private final DossierRepository dossiers;
	private final VuesFacture vues;
	private final Clock horloge;
	private final ApplicationEventPublisher evenements;
	private final EcrituresFacture ecritures;

	public CorrectionFactureService(FactureRepository factures, ChampLuRepository champsLus,
			ChangementStatutFactureRepository historique, CorrectionFactureRepository corrections,
			CompteComptableRepository comptes, DossierRepository dossiers, VuesFacture vues, Clock horloge,
			ApplicationEventPublisher evenements, EcrituresFacture ecritures) {
		this.factures = factures;
		this.champsLus = champsLus;
		this.historique = historique;
		this.corrections = corrections;
		this.comptes = comptes;
		this.dossiers = dossiers;
		this.vues = vues;
		this.horloge = horloge;
		this.evenements = evenements;
		this.ecritures = ecritures;
	}

	// RG2, RG3, RG7, RG8 : enregistre les informations, sans changer le statut.
	@Transactional
	public ResultatCorrection enregistrer(Compte collaborateur, Long dossierId, Long factureId,
			InformationsFacture informations) {
		return traiter(collaborateur, dossierId, factureId, informations, false);
	}

	// RG4 à RG6 : enregistre puis passe la facture en « Traitée » ; refusé, rien n'est enregistré.
	@Transactional
	public ResultatCorrection valider(Compte collaborateur, Long dossierId, Long factureId,
			InformationsFacture informations) {
		return traiter(collaborateur, dossierId, factureId, informations, true);
	}

	private ResultatCorrection traiter(Compte collaborateur, Long dossierId, Long factureId,
			InformationsFacture informations, boolean validation) {
		EvenementMetier evenement = EvenementMetier.de(validation ? "facture.validation" : "facture.correction",
				"traitee").avec(collaborateur.identifiantsPourLogs()).avec("dossierId", dossierId)
				.avec("factureId", factureId);
		Optional<Facture> trouvee = trouver(collaborateur, dossierId, factureId);
		if (trouvee.isEmpty()) {
			return refus(evenement, ResultatCorrection.Issue.INTROUVABLE, null);
		}
		Facture facture = trouvee.get();
		Optional<String> figee = figee(facture, validation);
		if (figee.isPresent()) {
			return refus(evenement, ResultatCorrection.Issue.FIGEE, figee.get());
		}
		InformationsFacture valeurs = informations == null ? VIDE : informations.nettoyees();
		Optional<String> invalide = refusInformations(facture, dossierId, valeurs, validation);
		if (invalide.isPresent()) {
			return refus(evenement, ResultatCorrection.Issue.INVALIDE, invalide.get());
		}
		List<ChampFacture> modifies = modifies(facture, valeurs);
		List<ChampLu> lus = champsLus.findByFactureId(facture.getId());
		lus.stream().filter(lu -> validation || modifies.contains(lu.getChamp())).forEach(ChampLu::lever);
		facture.corriger(valeurs, lus.stream().anyMatch(ChampLu::isAVerifier));
		Instant maintenant = horloge.instant();
		if (!modifies.isEmpty()) {
			corrections.save(new CorrectionFacture(facture,
					modifies.stream().map(Enum::name).collect(Collectors.joining(",")), collaborateur, maintenant));
		}
		ResultatCorrection.Issue issue = ResultatCorrection.Issue.ENREGISTREE;
		if (validation) {
			Facture.Statut ancien = facture.getStatut();
			facture.valider();
			historique.save(new ChangementStatutFacture(facture, ancien, Facture.Statut.TRAITEE, null, collaborateur,
					maintenant));
			issue = ResultatCorrection.Issue.VALIDEE;
			ecritures.generer(facture, collaborateur, maintenant);
			// Envoyé sur Kafka une fois la validation enregistrée (EvenementsFactureKafka).
			evenements.publishEvent(new FactureTraitee(facture.getId(), dossierId, facture.getSens(), maintenant));
		}
		evenement.avec("issue", issue).avec("champsModifies", modifies.size()).info();
		return new ResultatCorrection(issue, null, vues.vue(facture));
	}

	/**
	 * US-56 RG5 : recalcule l'écriture d'une facture d'achat traitée à partir de ses informations
	 * actuelles, tant qu'elle n'est pas exportée (CA2) ; exportée : refusé (CA3). Une facture traitée
	 * avant l'US-56 reçoit ainsi son écriture.
	 */
	@Transactional
	public ResultatCorrection regenerer(Compte collaborateur, Long dossierId, Long factureId) {
		EvenementMetier evenement = EvenementMetier.de("facture.regeneration_ecriture", "traitee")
				.avec(collaborateur.identifiantsPourLogs()).avec("dossierId", dossierId).avec("factureId", factureId);
		Optional<Facture> trouvee = trouver(collaborateur, dossierId, factureId);
		if (trouvee.isEmpty()) {
			return refus(evenement, ResultatCorrection.Issue.INTROUVABLE, null);
		}
		Facture facture = trouvee.get();
		Optional<String> figee = nonRegenerable(facture);
		if (figee.isPresent()) {
			return refus(evenement, ResultatCorrection.Issue.FIGEE, figee.get());
		}
		InformationsFacture actuelles = new InformationsFacture(facture.getTypeTiers(), facture.getNomTiers(),
				facture.getNumeroFacture(), facture.getDateEmission(), facture.getMontantHt(),
				facture.getMontantTva(), facture.getTauxTva(), facture.getMontantTtc(),
				facture.getCompteContrepartie());
		Optional<String> invalide = incomplete(actuelles).or(() -> ecritures.refus(facture, actuelles));
		if (invalide.isPresent()) {
			return refus(evenement, ResultatCorrection.Issue.INVALIDE, invalide.get());
		}
		ecritures.generer(facture, collaborateur, horloge.instant());
		evenement.avec("issue", ResultatCorrection.Issue.REGENEREE).info();
		return new ResultatCorrection(ResultatCorrection.Issue.REGENEREE, null, vues.vue(facture));
	}

	/**
	 * Formats et RG3, puis RG5 et RG4 pour une validation ou une facture traitée (US-56 : son écriture doit
	 * pouvoir se recalculer), puis l'écriture d'une facture d'achat validée (comptes du plan, équilibre).
	 */
	private Optional<String> refusInformations(Facture facture, Long dossierId, InformationsFacture valeurs,
			boolean validation) {
		boolean complete = validation || facture.getStatut() == Facture.Statut.TRAITEE;
		Optional<String> refus = invalide(dossierId, valeurs);
		if (refus.isEmpty() && complete) {
			refus = incomplete(valeurs);
		}
		if (refus.isEmpty() && validation && EcrituresFacture.concernee(facture)) {
			refus = ecritures.refus(facture, valeurs);
		}
		return refus;
	}

	private Optional<Facture> trouver(Compte collaborateur, Long dossierId, Long factureId) {
		return AccesDossier.dossierAccessible(dossiers, collaborateur, dossierId)
				.flatMap(dossier -> factures.findByIdAndDossierId(factureId, dossier.getId()));
	}

	private Optional<String> nonRegenerable(Facture facture) {
		if (!EcrituresFacture.concernee(facture)) {
			return Optional.of(MESSAGE_SANS_ECRITURE);
		}
		if (facture.getStatut() != Facture.Statut.TRAITEE) {
			return Optional.of(MESSAGE_NON_VALIDEE);
		}
		return ecritures.exportee(facture) ? Optional.of(MESSAGE_EXPORTEE) : Optional.empty();
	}

	private static ResultatCorrection refus(EvenementMetier evenement, ResultatCorrection.Issue issue,
			String message) {
		evenement.avec("issue", issue).warn();
		return ResultatCorrection.refuse(issue, message);
	}

	private Optional<String> figee(Facture facture, boolean validation) {
		if (facture.getStatut() == Facture.Statut.TRAITEE) {
			return traiteeFigee(facture, validation);
		}
		if (facture.getLectureStatut() == Facture.StatutLecture.EN_COURS) {
			return Optional.of(MESSAGE_LECTURE_EN_COURS);
		}
		return Optional.empty();
	}

	// US-56 : une facture d'achat traitée se corrige tant que son écriture n'est pas exportée.
	private Optional<String> traiteeFigee(Facture facture, boolean validation) {
		if (!EcrituresFacture.concernee(facture)) {
			return Optional.of(MESSAGE_TRAITEE);
		}
		if (ecritures.exportee(facture)) {
			return Optional.of(MESSAGE_EXPORTEE);
		}
		return validation ? Optional.of(MESSAGE_DEJA_VALIDEE) : Optional.empty();
	}

	// Formats et RG3 : premier refus dans l'ordre de la fiche.
	private Optional<String> invalide(Long dossierId, InformationsFacture valeurs) {
		List<Supplier<Optional<String>>> controles = List.of(
				() -> valeurs.typeTiers() == null || TYPES_TIERS.contains(valeurs.typeTiers()) ? Optional.empty()
						: Optional.of(MESSAGE_TYPE_TIERS),
				() -> trop(valeurs.nomTiers(), NOM_TIERS_MAX, "Nom du tiers"),
				() -> trop(valeurs.numeroFacture(), NUMERO_MAX, "Numéro de facture"),
				() -> montant(valeurs.montantHt(), "Montant HT"), () -> montant(valeurs.montantTva(), "Montant TVA"),
				() -> taux(valeurs.tauxTva()), () -> montant(valeurs.montantTtc(), "Montant TTC"),
				() -> contrepartie(dossierId, valeurs.compteContrepartie()));
		return controles.stream().map(Supplier::get).flatMap(Optional::stream).findFirst();
	}

	private static Optional<String> trop(String texte, int maximum, String libelle) {
		return texte != null && texte.length() > maximum
				? Optional.of(libelle + " : " + maximum + " caractères au plus")
				: Optional.empty();
	}

	private static Optional<String> montant(BigDecimal montant, String libelle) {
		if (montant == null || decimales(montant) <= 2 && montant.abs().compareTo(MONTANT_MAX) < 0) {
			return Optional.empty();
		}
		return Optional.of(libelle + " : nombre à 2 décimales au plus");
	}

	private static Optional<String> taux(BigDecimal taux) {
		if (taux == null || decimales(taux) <= 2 && taux.signum() >= 0 && taux.compareTo(TAUX_MAX) <= 0) {
			return Optional.empty();
		}
		return Optional.of(MESSAGE_TAUX);
	}

	private static int decimales(BigDecimal nombre) {
		return Math.max(0, nombre.stripTrailingZeros().scale());
	}

	// RG3 : la contrepartie se choisit dans le plan comptable du dossier, parmi les comptes actifs (US-41 RG3).
	private Optional<String> contrepartie(Long dossierId, String numero) {
		if (numero == null) {
			return Optional.empty();
		}
		Optional<CompteComptable> compte = comptes.findByDossierIdAndNumero(dossierId, numero);
		if (compte.isEmpty()) {
			return Optional.of("Le compte " + numero + " n'est pas dans le plan comptable du dossier");
		}
		return compte.get().isActif() ? Optional.empty()
				: Optional.of("Le compte " + numero + " est désactivé dans le plan comptable du dossier");
	}

	// RG5 puis RG4.
	private static Optional<String> incomplete(InformationsFacture valeurs) {
		Map<ChampFacture, Object> saisies = valeursParChamp(valeurs);
		Optional<String> manquant = OBLIGATOIRES.entrySet().stream()
				.filter(obligatoire -> saisies.get(obligatoire.getKey()) == null).map(Map.Entry::getValue)
				.findFirst();
		if (manquant.isPresent()) {
			return manquant;
		}
		boolean coherents = valeurs.montantHt().add(valeurs.montantTva()).compareTo(valeurs.montantTtc()) == 0;
		return coherents ? Optional.empty() : Optional.of(MESSAGE_INCOHERENTS);
	}

	// RG8 : champs dont la valeur change ; le taux vide d'une facture à plusieurs taux reste « Multiple ».
	private static List<ChampFacture> modifies(Facture facture, InformationsFacture valeurs) {
		Map<ChampFacture, Object> avant = new LinkedHashMap<>();
		avant.put(ChampFacture.TYPE_TIERS, facture.getTypeTiers());
		avant.put(ChampFacture.NOM_TIERS, facture.getNomTiers());
		avant.put(ChampFacture.NUMERO_FACTURE, facture.getNumeroFacture());
		avant.put(ChampFacture.DATE_EMISSION, facture.getDateEmission());
		avant.put(ChampFacture.MONTANT_HT, facture.getMontantHt());
		avant.put(ChampFacture.MONTANT_TVA, facture.getMontantTva());
		avant.put(ChampFacture.TAUX_TVA, facture.getTauxTva());
		avant.put(ChampFacture.MONTANT_TTC, facture.getMontantTtc());
		avant.put(ChampFacture.COMPTE_CONTREPARTIE, facture.getCompteContrepartie());
		Map<ChampFacture, Object> apres = valeursParChamp(valeurs);
		List<ChampFacture> modifies = new ArrayList<>();
		avant.forEach((champ, valeur) -> {
			boolean resteMultiple = champ == ChampFacture.TAUX_TVA && facture.isTauxTvaMultiple()
					&& valeurs.tauxTva() == null;
			if (!resteMultiple && !memeValeur(valeur, apres.get(champ))) {
				modifies.add(champ);
			}
		});
		return modifies;
	}

	private static boolean memeValeur(Object avant, Object apres) {
		if (avant instanceof BigDecimal nombreAvant && apres instanceof BigDecimal nombreApres) {
			return nombreAvant.compareTo(nombreApres) == 0;
		}
		return Objects.equals(avant, apres);
	}

	private static Map<ChampFacture, Object> valeursParChamp(InformationsFacture valeurs) {
		Map<ChampFacture, Object> parChamp = new LinkedHashMap<>();
		parChamp.put(ChampFacture.TYPE_TIERS, valeurs.typeTiers());
		parChamp.put(ChampFacture.NOM_TIERS, valeurs.nomTiers());
		parChamp.put(ChampFacture.NUMERO_FACTURE, valeurs.numeroFacture());
		parChamp.put(ChampFacture.DATE_EMISSION, valeurs.dateEmission());
		parChamp.put(ChampFacture.MONTANT_HT, valeurs.montantHt());
		parChamp.put(ChampFacture.MONTANT_TVA, valeurs.montantTva());
		parChamp.put(ChampFacture.TAUX_TVA, valeurs.tauxTva());
		parChamp.put(ChampFacture.MONTANT_TTC, valeurs.montantTtc());
		parChamp.put(ChampFacture.COMPTE_CONTREPARTIE, valeurs.compteContrepartie());
		return parChamp;
	}

	private static Map<ChampFacture, String> obligatoires() {
		Map<ChampFacture, String> messages = new LinkedHashMap<>();
		messages.put(ChampFacture.NOM_TIERS, "Indiquez le tiers");
		messages.put(ChampFacture.NUMERO_FACTURE, "Indiquez le numéro de facture");
		messages.put(ChampFacture.DATE_EMISSION, "Indiquez la date d'émission");
		messages.put(ChampFacture.MONTANT_HT, "Indiquez le montant HT");
		messages.put(ChampFacture.MONTANT_TVA, "Indiquez le montant de TVA");
		messages.put(ChampFacture.MONTANT_TTC, "Indiquez le montant TTC");
		messages.put(ChampFacture.COMPTE_CONTREPARTIE, "Indiquez le compte de contrepartie");
		return Collections.unmodifiableMap(messages);
	}

}
