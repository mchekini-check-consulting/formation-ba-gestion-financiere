package com.example.gestionfinanciere.banque;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.dossier.AccesDossier;
import com.example.gestionfinanciere.dossier.Dossier;
import com.example.gestionfinanciere.dossier.DossierRepository;
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

	public TransactionService(DossierRepository dossiers, ObjectProvider<FournisseurTransactions> fournisseurs) {
		this.dossiers = dossiers;
		this.fournisseurs = fournisseurs.orderedStream().toList();
	}

	// Vide si le dossier n'existe pas, est d'un autre cabinet ou n'est pas attribué au comptable.
	@Transactional(readOnly = true)
	public Optional<List<Transaction>> lister(Compte collaborateur, Long dossierId) {
		Optional<Dossier> dossier = AccesDossier.dossierAccessible(dossiers, collaborateur, dossierId);
		if (dossier.isEmpty()) {
			EvenementMetier.de("transaction.liste", "introuvable").avec(collaborateur.identifiantsPourLogs())
					.avec("dossierId", dossierId).warn();
			return Optional.empty();
		}
		List<Transaction> transactions = fournisseurs.stream()
				.flatMap(fournisseur -> fournisseur.transactions(dossier.get()).stream())
				.sorted(PLUS_RECENTES_D_ABORD).toList();
		EvenementMetier.de("transaction.liste", "consultee").avec(collaborateur.identifiantsPourLogs())
				.avec("dossierId", dossierId).avec("nombre", transactions.size()).info();
		return Optional.of(transactions);
	}

}
