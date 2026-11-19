package com.example.gestionfinanciere.banque;

import java.util.List;

import com.example.gestionfinanciere.dossier.Dossier;

/**
 * US-58 « Prise » unique des transactions bancaires d'un dossier. Deux implémentations : le
 * bouchon (BouchonTransactions, données de démonstration) et, plus tard, la connexion bancaire
 * (US-21, US-22). TransactionService réunit toutes celles qui sont activées ; aucune activée :
 * « Aucune transaction ».
 */
public interface FournisseurTransactions {

	List<Transaction> transactions(Dossier dossier);

}
