package com.example.gestionfinanciere.ecriture;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.Loggable;
import com.example.gestionfinanciere.plancomptable.CompteComptable;
import com.example.gestionfinanciere.plancomptable.CompteComptableRepository;

/**
 * US-56 Écritures des factures d'achat validées. L'appelant vérifie les droits sur le dossier et
 * appelle refus() avant enregistrer(). RG2 : une écriture est toujours équilibrée ; comme pour
 * l'US-30, elle n'utilise que des comptes actifs du plan comptable du dossier (US-41 RG3).
 */
@Service
@Loggable
public class EcritureService {

	static final String MESSAGE_DESEQUILIBREE = "Écriture déséquilibrée : total au débit ≠ total au crédit";

	private final EcritureRepository ecritures;
	private final CompteComptableRepository comptes;

	public EcritureService(EcritureRepository ecritures, CompteComptableRepository comptes) {
		this.ecritures = ecritures;
		this.comptes = comptes;
	}

	public Optional<Ecriture> deLaFacture(Long factureId) {
		return ecritures.findByFactureId(factureId);
	}

	// RG2 et comptes du plan : premier refus, ou vide si l'écriture peut être enregistrée.
	public Optional<String> refus(Long dossierId, CalculEcriture.Resultat calcul) {
		if (!calcul.equilibree()) {
			return Optional.of(MESSAGE_DESEQUILIBREE);
		}
		return refusComptes(dossierId, calcul.lignes().stream().map(CalculEcriture.Ligne::numeroCompte).toList());
	}

	// Comptes absents ou désactivés du plan du dossier : premier refus, ou vide (US-47 RG3 aussi).
	Optional<String> refusComptes(Long dossierId, Collection<String> numeros) {
		return numeros.stream().distinct().map(numero -> refusCompte(dossierId, numero)).flatMap(Optional::stream)
				.findFirst();
	}

	private Optional<String> refusCompte(Long dossierId, String numero) {
		Optional<CompteComptable> compte = comptes.findByDossierIdAndNumero(dossierId, numero);
		if (compte.isEmpty()) {
			return Optional.of("Le compte " + numero + " n'est pas dans le plan comptable du dossier");
		}
		return compte.get().isActif() ? Optional.empty()
				: Optional.of("Le compte " + numero + " est désactivé dans le plan comptable du dossier");
	}

	/**
	 * RG1 : crée l'écriture de la facture ; RG5 : la recalcule si elle existe déjà. L'appelant a
	 * vérifié qu'elle n'est pas exportée et que refus() est vide.
	 */
	public Ecriture enregistrer(Long dossierId, Long factureId, CalculEcriture.Resultat calcul, Compte auteur,
			Instant quand) {
		Optional<Ecriture> existante = ecritures.findByFactureId(factureId);
		Ecriture ecriture = existante.orElseGet(() -> Ecriture.achat(dossierId, factureId));
		ecriture.remplir(calcul, auteur, quand);
		Ecriture enregistree = ecritures.save(ecriture);
		EvenementMetier.de("ecriture.generation", existante.isPresent() ? "regeneree" : "generee")
				.avec(auteur.identifiantsPourLogs()).avec("dossierId", dossierId).avec("factureId", factureId)
				.avec("lignes", calcul.lignes().size()).info();
		return enregistree;
	}

	// RG6 : écriture affichée ; actuelle = calcul à partir de la facture d'aujourd'hui (null : non calculable).
	public VueEcriture vue(Ecriture ecriture, CalculEcriture.Resultat actuelle) {
		return vue(ecriture, ecriture.enValeur().equals(actuelle));
	}

	// RG6 : écriture affichée, aJour connu de l'appelant (US-47 : écriture corrigée à la main).
	public VueEcriture vue(Ecriture ecriture, boolean aJour) {
		return vue(ecriture, aJour, libelles(ecriture.getDossierId()));
	}

	// Libellés des comptes du plan du dossier, par numéro.
	Map<String, String> libelles(Long dossierId) {
		return comptes.findByDossierIdOrderByNumero(dossierId).stream()
				.collect(Collectors.toMap(CompteComptable::getNumero, CompteComptable::getLibelle, (a, b) -> a));
	}

	VueEcriture vue(Ecriture ecriture, boolean aJour, Map<String, String> libelles) {
		CalculEcriture.Resultat enregistree = ecriture.enValeur();
		Function<CalculEcriture.Ligne, VueEcriture.Ligne> ligne = l -> new VueEcriture.Ligne(l.numeroCompte(),
				libelles.get(l.numeroCompte()), l.libelle(), l.debit(), l.credit());
		Compte modifieePar = ecriture.getModifieePar();
		return new VueEcriture(ecriture.getJournal(), ecriture.getDateEcriture(), ecriture.getLibelle(),
				ecriture.getGenereeLe(), ecriture.getGenereePar().getNomAffiche(), ecriture.getModifieeLe(),
				modifieePar == null ? null : modifieePar.getNomAffiche(), ecriture.isExportee(), aJour,
				enregistree.lignes().stream().map(ligne).toList(), enregistree.totalDebit(),
				enregistree.totalCredit());
	}

}
