package com.example.gestionfinanciere.ecriture;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.compte.Compte;
import com.example.gestionfinanciere.dossier.AccesDossier;
import com.example.gestionfinanciere.dossier.DossierRepository;
import com.example.gestionfinanciere.ecriture.ResultatEcriture.Statut;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.Loggable;

/**
 * US-47 Journal d'un dossier et correction d'une écriture. Mêmes droits que le dossier (US-11 RG3,
 * US-20) : un dossier inaccessible donne la même réponse qu'un dossier inexistant.
 * RG1 : écritures d'une période (un mois ou l'exercice civil). RG2 : une écriture non exportée se
 * corrige (libellé, compte, débit, crédit) ; exportée, elle est en lecture seule. RG3 : refus d'une
 * écriture déséquilibrée ou d'un compte absent ou désactivé du plan. RG4 : auteur et date gardés.
 */
@Service
@Loggable
public class JournalEcrituresService {

	static final String MESSAGE_EXPORTEE = "Écriture exportée : elle ne se modifie plus";
	static final String MESSAGE_LIBELLE = "Le libellé est obligatoire";
	static final String MESSAGE_LIBELLE_LONG = "Le libellé fait au plus 400 caractères";
	static final String MESSAGE_NOMBRE_LIGNES = "Une écriture corrigée garde le même nombre de lignes";
	static final String MESSAGE_COMPTE = "Chaque ligne a un compte";
	static final String MESSAGE_MONTANT =
			"Chaque ligne a un seul montant, au débit ou au crédit, positif et à 2 décimales au plus";
	static final String PREFIXE_DESEQUILIBREE = "Écriture déséquilibrée : écart de ";

	private static final int LIBELLE_MAX = 400;
	private static final BigDecimal MONTANT_MAX = new BigDecimal("9999999999.99");
	private static final String LOG_DOSSIER = "dossierId";
	private static final String LOG_ECRITURE = "ecritureId";

	private final EcritureRepository ecritures;
	private final EcritureService service;
	private final DossierRepository dossiers;
	private final Clock horloge;

	public JournalEcrituresService(EcritureRepository ecritures, EcritureService service, DossierRepository dossiers,
			Clock horloge) {
		this.ecritures = ecritures;
		this.service = service;
		this.dossiers = dossiers;
		this.horloge = horloge;
	}

	/**
	 * RG1 : journal de la période demandée (« 2026-06 » ou « 2026 ») ; une période sans écriture ou mal
	 * écrite donne le mois le plus récent qui en a. Vide si le dossier n'est pas accessible.
	 */
	@Transactional(readOnly = true)
	public Optional<JournalEcritures> consulter(Compte collaborateur, Long dossierId, String periode) {
		if (!accessible(collaborateur, dossierId)) {
			return Optional.empty();
		}
		List<JournalEcritures.Periode> periodes = periodes(ecritures.datesDuDossier(dossierId));
		if (periodes.isEmpty()) {
			return Optional.of(new JournalEcritures(null, periodes, List.of(), BigDecimal.ZERO, BigDecimal.ZERO));
		}
		String choisie = periodes.stream().map(JournalEcritures.Periode::code).filter(code -> code.equals(periode))
				.findFirst().orElse(periodes.get(0).code());
		LocalDate du = debut(choisie);
		LocalDate au = choisie.length() == 4 ? du.plusYears(1).minusDays(1) : YearMonth.from(du).atEndOfMonth();
		Map<String, String> libelles = service.libelles(dossierId);
		List<JournalEcritures.Ligne> lignes = ecritures
				.findByDossierIdAndDateEcritureBetweenOrderByDateEcritureAscIdAsc(dossierId, du, au).stream()
				.map(ecriture -> JournalEcritures.Ligne.de(ecriture, service.vue(ecriture, true, libelles))).toList();
		return Optional.of(new JournalEcritures(choisie, periodes, lignes,
				lignes.stream().map(JournalEcritures.Ligne::totalDebit).reduce(BigDecimal.ZERO, BigDecimal::add),
				lignes.stream().map(JournalEcritures.Ligne::totalCredit).reduce(BigDecimal.ZERO, BigDecimal::add)));
	}

	// Mois qui ont des écritures, du plus récent au plus ancien, chaque exercice après ses mois.
	static List<JournalEcritures.Periode> periodes(List<LocalDate> dates) {
		TreeSet<YearMonth> mois = new TreeSet<>(Comparator.reverseOrder());
		dates.forEach(date -> mois.add(YearMonth.from(date)));
		List<JournalEcritures.Periode> periodes = new ArrayList<>();
		for (YearMonth m : mois) {
			periodes.add(new JournalEcritures.Periode(m.toString(), libelle(m)));
			YearMonth suivant = mois.higher(m);
			if (suivant == null || suivant.getYear() != m.getYear()) {
				periodes.add(new JournalEcritures.Periode(String.valueOf(m.getYear()), "Exercice " + m.getYear()));
			}
		}
		return periodes;
	}

	// « Juin 2026 ».
	private static String libelle(YearMonth mois) {
		String nom = mois.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, Locale.FRENCH);
		return Character.toUpperCase(nom.charAt(0)) + nom.substring(1) + " " + mois.getYear();
	}

	private static LocalDate debut(String periode) {
		return periode.length() == 4 ? Year.parse(periode).atDay(1) : YearMonth.parse(periode).atDay(1);
	}

	/** RG2 à RG4 : correction d'une écriture du dossier ; rien n'est enregistré en cas de refus. */
	@Transactional
	public ResultatEcriture modifier(Compte collaborateur, Long dossierId, Long ecritureId,
			CorrectionEcriture correction) {
		if (!accessible(collaborateur, dossierId)) {
			return ResultatEcriture.de(Statut.DOSSIER_INTROUVABLE);
		}
		Optional<Ecriture> trouvee = ecritures.findByIdAndDossierId(ecritureId, dossierId);
		if (trouvee.isEmpty()) {
			return ResultatEcriture.de(Statut.ECRITURE_INTROUVABLE);
		}
		Ecriture ecriture = trouvee.get();
		EvenementMetier evenement = EvenementMetier.de("ecriture.correction", "refusee")
				.avec(collaborateur.identifiantsPourLogs()).avec(LOG_DOSSIER, dossierId).avec(LOG_ECRITURE, ecritureId);
		if (ecriture.isExportee()) {
			evenement.avec("motif", "exportee").warn();
			return ResultatEcriture.refus(Statut.EXPORTEE, MESSAGE_EXPORTEE);
		}
		Optional<String> refus = invalide(correction, ecriture.getLignes().size());
		List<CalculEcriture.Ligne> lignes = refus.isPresent() ? List.of() : lignes(correction);
		refus = refus.or(() -> desequilibre(lignes))
				.or(() -> service.refusComptes(dossierId, lignes.stream().map(CalculEcriture.Ligne::numeroCompte).toList()));
		if (refus.isPresent()) {
			evenement.avec("motif", "invalide").warn();
			return ResultatEcriture.refus(Statut.INVALIDE, refus.get());
		}
		ecriture.corriger(correction.libelle().trim(), lignes, collaborateur, horloge.instant());
		Ecriture enregistree = ecritures.save(ecriture);
		EvenementMetier.de("ecriture.correction", "modifiee").avec(collaborateur.identifiantsPourLogs())
				.avec(LOG_DOSSIER, dossierId).avec(LOG_ECRITURE, ecritureId).info();
		return ResultatEcriture.modifiee(JournalEcritures.Ligne.de(enregistree, service.vue(enregistree, true)));
	}

	// Formulaire : libellé, même nombre de lignes, un compte et un seul montant par ligne.
	static Optional<String> invalide(CorrectionEcriture correction, int nombreLignes) {
		String libelle = correction == null ? null : correction.libelle();
		if (libelle == null || libelle.isBlank()) {
			return Optional.of(MESSAGE_LIBELLE);
		}
		if (libelle.trim().length() > LIBELLE_MAX) {
			return Optional.of(MESSAGE_LIBELLE_LONG);
		}
		if (correction.lignes() == null || correction.lignes().size() != nombreLignes) {
			return Optional.of(MESSAGE_NOMBRE_LIGNES);
		}
		for (CorrectionEcriture.LigneCorrigee ligne : correction.lignes()) {
			if (ligne == null || ligne.numeroCompte() == null || ligne.numeroCompte().isBlank()) {
				return Optional.of(MESSAGE_COMPTE);
			}
			if (!montantsValides(ligne.debit(), ligne.credit())) {
				return Optional.of(MESSAGE_MONTANT);
			}
		}
		return Optional.empty();
	}

	private static boolean montantsValides(BigDecimal debit, BigDecimal credit) {
		BigDecimal d = debit == null ? BigDecimal.ZERO : debit;
		BigDecimal c = credit == null ? BigDecimal.ZERO : credit;
		return valide(d) && valide(c) && (d.signum() == 0) != (c.signum() == 0);
	}

	private static boolean valide(BigDecimal montant) {
		return montant.signum() >= 0 && montant.stripTrailingZeros().scale() <= 2 && montant.compareTo(MONTANT_MAX) <= 0;
	}

	private static List<CalculEcriture.Ligne> lignes(CorrectionEcriture correction) {
		return correction.lignes().stream()
				.map(l -> new CalculEcriture.Ligne(l.numeroCompte().trim(), "",
						l.debit() == null ? BigDecimal.ZERO : l.debit(),
						l.credit() == null ? BigDecimal.ZERO : l.credit()))
				.toList();
	}

	// RG3, CA2 : « Écriture déséquilibrée : écart de 10,00 € ».
	static Optional<String> desequilibre(List<CalculEcriture.Ligne> lignes) {
		BigDecimal debit = lignes.stream().map(CalculEcriture.Ligne::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal credit = lignes.stream().map(CalculEcriture.Ligne::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
		if (debit.compareTo(credit) == 0) {
			return Optional.empty();
		}
		return Optional.of(PREFIXE_DESEQUILIBREE + euros(debit.subtract(credit).abs()));
	}

	static String euros(BigDecimal montant) {
		NumberFormat format = NumberFormat.getNumberInstance(Locale.FRANCE);
		format.setMinimumFractionDigits(2);
		format.setMaximumFractionDigits(2);
		return format.format(montant) + " €";
	}

	private boolean accessible(Compte collaborateur, Long dossierId) {
		return AccesDossier.dossierAccessible(dossiers, collaborateur, dossierId).isPresent();
	}

}
