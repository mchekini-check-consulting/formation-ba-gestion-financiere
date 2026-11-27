package com.example.gestionfinanciere.banque;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.dossier.AccesDossier;
import com.example.gestionfinanciere.dossier.Dossier;
import com.example.gestionfinanciere.dossier.DossierRepository;
import com.example.gestionfinanciere.ecriture.CalculEcriture;
import com.example.gestionfinanciere.ecriture.EcritureService;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.Loggable;
import com.example.gestionfinanciere.plancomptable.Categorie;
import com.example.gestionfinanciere.plancomptable.CategorieRepository;

/**
 * US-30 Valider une opération bancaire et générer son écriture, au journal de banque (BQ).
 * RG1 : compte de la catégorie (par défaut celui de la catégorie, US-41 RG2), compte de TVA si le
 * taux n'est pas nul (déductible 44566, collectée 44571 pour une vente), banque 512. RG2 : écriture
 * toujours équilibrée ; comme pour les factures (US-56), uniquement des comptes actifs du plan du
 * dossier, sinon rien n'est enregistré. Une opération ne se valide qu'une fois. Mêmes droits que le
 * dossier (US-20) ; l'appelant a vérifié que la personne connectée est un collaborateur du cabinet.
 */
@Service
@Loggable
public class ValidationOperationService {

	static final String MESSAGE_INTROUVABLE = "Opération introuvable";
	static final String MESSAGE_DEJA_VALIDEE = "Opération déjà validée";
	static final String MESSAGE_CATEGORIE = "Indiquez la catégorie : Achat, Vente ou Frais bancaires";
	static final String MESSAGE_TAUX = "Taux de TVA : entre 0 et 100 %";

	private static final String CATEGORIE_VENTE = "VENTE";
	private static final BigDecimal TAUX_MAX = BigDecimal.valueOf(100);

	private final DossierRepository dossiers;
	private final TransactionService transactions;
	private final OperationValideeRepository operations;
	private final CategorieRepository categories;
	private final EcritureService ecritures;
	private final Clock horloge;

	public ValidationOperationService(DossierRepository dossiers, TransactionService transactions,
			OperationValideeRepository operations, CategorieRepository categories, EcritureService ecritures,
			Clock horloge) {
		this.dossiers = dossiers;
		this.transactions = transactions;
		this.operations = operations;
		this.categories = categories;
		this.ecritures = ecritures;
		this.horloge = horloge;
	}

	@Transactional
	public ResultatValidation valider(Compte collaborateur, Long dossierId, String reference,
			DemandeValidation demande) {
		EvenementMetier evenement = EvenementMetier.de("operation.validation", "validee")
				.avec(collaborateur.identifiantsPourLogs()).avec("dossierId", dossierId);
		Optional<Dossier> dossier = AccesDossier.dossierAccessible(dossiers, collaborateur, dossierId);
		Optional<Transaction> transaction = dossier.flatMap(d -> transactions.transactions(d).stream()
				.filter(t -> t.reference().equals(reference)).findFirst());
		if (transaction.isEmpty()) {
			return refus(evenement, ResultatValidation.Issue.INTROUVABLE, MESSAGE_INTROUVABLE);
		}
		if (operations.findByDossierIdAndReference(dossierId, reference).isPresent()) {
			return refus(evenement, ResultatValidation.Issue.DEJA_VALIDEE, MESSAGE_DEJA_VALIDEE);
		}
		Optional<Categorie> categorie = Optional.ofNullable(demande).map(DemandeValidation::categorie)
				.flatMap(categories::findByCode);
		if (categorie.isEmpty()) {
			return refus(evenement, ResultatValidation.Issue.INVALIDE, MESSAGE_CATEGORIE);
		}
		BigDecimal taux = demande.tauxTva();
		if (taux == null || taux.signum() < 0 || taux.compareTo(TAUX_MAX) > 0 || taux.stripTrailingZeros().scale() > 2) {
			return refus(evenement, ResultatValidation.Issue.INVALIDE, MESSAGE_TAUX);
		}
		String compte = demande.numeroCompte() == null || demande.numeroCompte().isBlank()
				? categorie.get().getNumeroCompte() : demande.numeroCompte().strip();
		Transaction t = transaction.get();
		CalculEcriture.Resultat calcul = CalculEcriture.operation(t.date(), t.libelle(), t.montant(),
				t.sens() == Transaction.Sens.SORTIE, compte, compteTva(categorie.get()), taux);
		Optional<String> refus = ecritures.refus(dossierId, calcul);
		if (refus.isPresent()) {
			return refus(evenement, ResultatValidation.Issue.INVALIDE, refus.get());
		}
		Instant maintenant = horloge.instant();
		OperationValidee operation = operations.save(new OperationValidee(dossierId, t, categorie.get().getCode(),
				compte, taux, collaborateur, maintenant));
		ecritures.enregistrerOperation(dossierId, operation.getId(), calcul, collaborateur, maintenant);
		evenement.avec("issue", ResultatValidation.Issue.VALIDEE).avec("categorie", operation.getCategorie()).info();
		return new ResultatValidation(ResultatValidation.Issue.VALIDEE, null, transactions.vue(t, operation));
	}

	// RG1 : TVA collectée pour une vente, déductible sinon.
	static String compteTva(Categorie categorie) {
		return CATEGORIE_VENTE.equals(categorie.getCode()) ? CalculEcriture.COMPTE_TVA_COLLECTEE
				: CalculEcriture.COMPTE_TVA_DEDUCTIBLE;
	}

	private static ResultatValidation refus(EvenementMetier evenement, ResultatValidation.Issue issue,
			String message) {
		evenement.avec("issue", issue).warn();
		return ResultatValidation.refuse(issue, message);
	}

}
