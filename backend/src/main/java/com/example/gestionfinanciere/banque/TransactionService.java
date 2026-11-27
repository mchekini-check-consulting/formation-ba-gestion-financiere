package com.example.gestionfinanciere.banque;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.dossier.AccesDossier;
import com.example.gestionfinanciere.dossier.Dossier;
import com.example.gestionfinanciere.dossier.DossierRepository;
import com.example.gestionfinanciere.ecriture.EcritureService;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.Loggable;

/**
 * US-58 Transactions bancaires d'un dossier, réunies depuis tous les fournisseurs activés (le
 * bouchon aujourd'hui, la connexion bancaire plus tard), les plus récentes d'abord. Aucun
 * fournisseur activé : liste vide (« Aucune transaction »). L'appelant vérifie avant que la
 * personne connectée est un collaborateur d'un cabinet ; ici, mêmes droits que le dossier (US-20).
 */
@Service
@Loggable
public class TransactionService {

	private static final Comparator<Transaction> PLUS_RECENTES_D_ABORD = Comparator
			.comparing(Transaction::date).reversed().thenComparing(Transaction::libelle);

	private final DossierRepository dossiers;
	private final List<FournisseurTransactions> fournisseurs;
	private final OperationValideeRepository operations;
	private final EcritureService ecritures;

	public TransactionService(DossierRepository dossiers, ObjectProvider<FournisseurTransactions> fournisseurs,
			OperationValideeRepository operations, EcritureService ecritures) {
		this.dossiers = dossiers;
		this.fournisseurs = fournisseurs.orderedStream().toList();
		this.operations = operations;
		this.ecritures = ecritures;
	}

	// Vide si le dossier n'existe pas, est d'un autre cabinet ou n'est pas attribué au comptable.
	// US-30 : chaque transaction porte sa validation et son écriture, si elle est validée.
	@Transactional(readOnly = true)
	public Optional<List<VueTransaction>> lister(Compte collaborateur, Long dossierId) {
		Optional<Dossier> dossier = AccesDossier.dossierAccessible(dossiers, collaborateur, dossierId);
		if (dossier.isEmpty()) {
			EvenementMetier.de("transaction.liste", "introuvable").avec(collaborateur.identifiantsPourLogs())
					.avec("dossierId", dossierId).warn();
			return Optional.empty();
		}
		List<Transaction> transactions = transactions(dossier.get());
		Map<String, OperationValidee> validees = operations.findByDossierId(dossierId).stream()
				.collect(Collectors.toMap(OperationValidee::getReference, Function.identity(), (a, b) -> a));
		EvenementMetier.de("transaction.liste", "consultee").avec(collaborateur.identifiantsPourLogs())
				.avec("dossierId", dossierId).avec("nombre", transactions.size()).info();
		return Optional.of(transactions.stream().map(t -> vue(t, validees.get(t.reference()))).toList());
	}

	// Transactions du dossier, tous fournisseurs activés réunis, les plus récentes d'abord.
	public List<Transaction> transactions(Dossier dossier) {
		return fournisseurs.stream().flatMap(fournisseur -> fournisseur.transactions(dossier).stream())
				.sorted(PLUS_RECENTES_D_ABORD).toList();
	}

	// US-30 : ligne de l'onglet Banque ; operation vide tant que la transaction n'est pas validée.
	public VueTransaction vue(Transaction transaction, OperationValidee operation) {
		if (operation == null) {
			return VueTransaction.de(transaction, null);
		}
		return VueTransaction.de(transaction,
				new VueTransaction.Validation(operation.getCategorie(), operation.getNumeroCompte(),
						operation.getTauxTva(), operation.getValideePar().getNomAffiche(), operation.getValideeLe(),
						ecritures.deLOperation(operation.getId()).map(ecritures::vue).orElse(null)));
	}

}
