package com.example.gestionfinanciere.facture.lecture;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.gestionfinanciere.facture.ChampFacture;

/**
 * US-53 : repère les champs d'une facture fournisseur dans son texte (lu dans le PDF ou par l'OCR).
 * US-54 : même lecture pour une facture client ; le tiers est alors le destinataire (RG3), jamais
 * l'entreprise du dossier qui l'émet, et la contrepartie un compte de produit (RG2). Plusieurs taux de
 * TVA (CA2, les deux sens) : taux vide (« Multiple ») et détail par taux.
 * Chaque champ reçoit un niveau de confiance : sous {@link #SEUIL}, il est « À vérifier » (RG4) ;
 * HT + TVA différent du TTC : les trois montants sont « À vérifier » (RG5) ; tiers introuvable :
 * vide, « À renseigner » (RG3) ; aucun montant : « Lecture échouée » (CA4).
 */
public final class AnalyseurFacture {

	// RG4 : sous ce niveau de confiance, le champ est « À vérifier ».
	public static final double SEUIL = 0.8;
	public static final String FOURNISSEUR = "FOURNISSEUR";
	public static final String CLIENT = "CLIENT";

	private static final double SUR = 0.95;
	private static final double PROBABLE = 0.85;
	private static final double DEVINE = 0.7;
	private static final double FAIBLE = 0.5;
	private static final BigDecimal CENT = BigDecimal.valueOf(100);
	private static final BigDecimal ECART_TOLERE = new BigDecimal("0.01");
	private static final List<BigDecimal> TAUX_CONNUS = List.of(new BigDecimal("20.00"), new BigDecimal("10.00"),
			new BigDecimal("5.50"), new BigDecimal("2.10"));

	// Montant « 1 234,56 », « 1.234,56 » ou « 186.40 », jamais un pourcentage : lu caractère par caractère
	// (pas d'expression régulière, temps de lecture proportionnel à la longueur de la ligne).
	private static final String CHIFFRES_ET_SEPARATEURS = "0123456789,.";
	private static final Pattern POURCENTAGE = Pattern.compile("(\\d{1,2}(?:[,.]\\d{1,2})?+)\\s*+%");
	private static final Pattern DATE_CHIFFRES = Pattern.compile("(?<!\\d)(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4}|\\d{2})(?!\\d)");
	private static final Pattern DATE_LETTRES = Pattern.compile(
			"(?<!\\d)(\\d{1,2})(?:er)?+\\s++(janvier|fevrier|mars|avril|mai|juin|juillet|aout|septembre|octobre|novembre|decembre)\\s++(\\d{4})");
	private static final List<String> MOIS = List.of("janvier", "fevrier", "mars", "avril", "mai", "juin", "juillet",
			"aout", "septembre", "octobre", "novembre", "decembre");
	private static final Pattern NUMERO_FACTURE = Pattern.compile(
			"(?:facture|invoice)\\s*+(?:n\\s?+[°o]\\.?+|num(?:ero)?+\\.?+|#)\\s*+:?+\\s*+([a-z0-9][a-z0-9\\-_/.]{2,40}+)");
	private static final Pattern NUMERO = Pattern.compile("(?:^|\\s)n\\s?+[°o]\\.?+\\s*+:?+\\s*+([a-z0-9][a-z0-9\\-_/.]{2,40}+)");
	private static final Pattern TIERS_ANNONCE = Pattern
			.compile("(?:fournisseur|emetteur|vendeur|societe)\\s*+:\\s*+([^\\n]{2,80}+)");
	// US-54 RG3 : destinataire d'une facture client (« Client : ... », « Facturé à », « Destinataire »).
	private static final Pattern DESTINATAIRE_ANNONCE = Pattern.compile(
			"^(?:client|destinataire|factur(?:e|ee) a|adresse a|livre a|a l'?\\s?attention de)(?![a-z])\\s*:?\\s*(.{0,80})$");
	private static final Pattern NUMERO_SEUL = Pattern.compile("^(?:n\\s?[°o]|code|ref|numero)(?![a-z]).*");
	private static final BigDecimal TAUX_MAX = new BigDecimal("30");
	private static final BigDecimal ECART_TAUX = new BigDecimal("0.02");

	// RG3 : fournisseurs reconnus à coup sûr (nom tel qu'affiché, mot cherché dans le texte).
	private static final Map<String, String> FOURNISSEURS_CONNUS = new LinkedHashMap<>();
	// Contrepartie proposée : premier mot-clé trouvé -> compte du plan comptable.
	private static final Map<String, String> COMPTES_PAR_MOT = new LinkedHashMap<>();
	// US-54 RG2 : compte de produit proposé pour une facture client.
	private static final Map<String, String> PRODUITS_PAR_MOT = new LinkedHashMap<>();
	// Libellés des comptes proposés (page de la facture).
	private static final Map<String, String> LIBELLES_COMPTES = new LinkedHashMap<>();
	static final String COMPTE_PAR_DEFAUT = "607";
	static final String PRODUIT_PAR_DEFAUT = "706";

	static {
		for (String nom : List.of("EDF", "Engie", "TotalEnergies", "Veolia", "Orange", "SFR", "Free", "Bouygues Telecom",
				"La Poste", "SNCF", "Leroy Merlin", "Castorama", "Brico Pro", "Metro", "AXA", "MAIF", "Allianz",
				"Office Depot", "Bureau Vallée", "Amazon", "Moulins Bertrand", "Emballages Sud")) {
			FOURNISSEURS_CONNUS.put(nom, sansAccents(nom.toLowerCase(Locale.ROOT)));
		}
		LIBELLES_COMPTES.put("6061", "Fournitures non stockables (eau, énergie)");
		LIBELLES_COMPTES.put("6063", "Fournitures d'entretien et de petit équipement");
		LIBELLES_COMPTES.put("6064", "Fournitures administratives");
		LIBELLES_COMPTES.put("613", "Locations");
		LIBELLES_COMPTES.put("616", "Primes d'assurance");
		LIBELLES_COMPTES.put("625", "Déplacements, missions et réceptions");
		LIBELLES_COMPTES.put("626", "Frais postaux et de télécommunications");
		LIBELLES_COMPTES.put(COMPTE_PAR_DEFAUT, "Achats de marchandises");
		LIBELLES_COMPTES.put(PRODUIT_PAR_DEFAUT, "Prestations de services");
		LIBELLES_COMPTES.put("707", "Ventes de marchandises");
		LIBELLES_COMPTES.put("708", "Produits des activités annexes");
		for (String mot : List.of("location", "loyer", "refacturation", "frais de port", "commission")) {
			PRODUITS_PAR_MOT.put(mot, "708");
		}
		for (String mot : List.of("marchandise", "marchandises", "article", "articles", "vente de", "livraison de")) {
			PRODUITS_PAR_MOT.put(mot, "707");
		}
		for (String mot : List.of("prestation", "prestations", "service", "services", "honoraires", "conseil",
				"formation", "maintenance", "intervention", "main d'oeuvre", "installation", "reparation")) {
			PRODUITS_PAR_MOT.put(mot, PRODUIT_PAR_DEFAUT);
		}
		for (String mot : List.of("edf", "engie", "totalenergies", "veolia", "electricite", "gaz", "eau", "energie",
				"carburant", "gazole")) {
			COMPTES_PAR_MOT.put(mot, "6061");
		}
		for (String mot : List.of("orange", "sfr", "free", "bouygues", "la poste", "telephone", "internet", "mobile")) {
			COMPTES_PAR_MOT.put(mot, "626");
		}
		for (String mot : List.of("loyer", "location", "bail")) {
			COMPTES_PAR_MOT.put(mot, "613");
		}
		for (String mot : List.of("assurance", "axa", "maif", "allianz")) {
			COMPTES_PAR_MOT.put(mot, "616");
		}
		for (String mot : List.of("sncf", "train", "hotel", "restaurant", "taxi", "peage")) {
			COMPTES_PAR_MOT.put(mot, "625");
		}
		for (String mot : List.of("leroy merlin", "castorama", "brico", "outillage", "entretien", "nettoyage")) {
			COMPTES_PAR_MOT.put(mot, "6063");
		}
		for (String mot : List.of("office depot", "bureau vallee", "papeterie", "fournitures de bureau", "cartouche")) {
			COMPTES_PAR_MOT.put(mot, "6064");
		}
	}

	private AnalyseurFacture() {
	}

	// Valeur lue et son niveau de confiance.
	private record Lu<T>(T valeur, double confiance) {
	}

	// Libellé d'un compte proposé (page de la facture) ; null si le compte n'est pas connu.
	public static String libelleCompte(String compte) {
		return LIBELLES_COMPTES.get(compte);
	}

	// US-53 : facture fournisseur.
	public static ResultatLecture analyser(String texte) {
		return analyser(texte, false, null);
	}

	// US-54 : facture client émise par l'entreprise du dossier (son nom n'est jamais pris pour le client).
	public static ResultatLecture analyserFactureClient(String texte, String entrepriseDuDossier) {
		return analyser(texte, true, entrepriseDuDossier);
	}

	private static ResultatLecture analyser(String texte, boolean client, String entrepriseDuDossier) {
		String propre = texte == null ? "" : texte.replace('\u00A0', ' ').replace('\u202F', ' ');
		List<String> lignes = propre.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
		String tout = sansAccents(String.join("\n", lignes).toLowerCase(Locale.ROOT));

		Montants montants = montants(lignes);
		if (montants.ht == null && montants.tva == null && montants.ttc == null) {
			return ResultatLecture.illisible();
		}
		Lu<String> tiers = client ? destinataire(lignes, entrepriseDuDossier) : tiers(lignes, tout);
		Lu<String> numero = numero(tout);
		Lu<LocalDate> date = date(lignes);
		List<TvaParTaux> detail = detailTva(lignes);
		boolean plusieursTaux = detail.size() > 1;
		Lu<BigDecimal> taux = plusieursTaux ? tauxMultiple(detail, montants) : taux(lignes, montants);
		Lu<String> compte = client ? compte(tout, PRODUITS_PAR_MOT, PRODUIT_PAR_DEFAUT)
				: compte(tout, COMPTES_PAR_MOT, COMPTE_PAR_DEFAUT);

		Map<ChampFacture, Double> confiances = new EnumMap<>(ChampFacture.class);
		confiances.put(ChampFacture.TYPE_TIERS, 1.0);
		noter(confiances, ChampFacture.NOM_TIERS, tiers);
		noter(confiances, ChampFacture.NUMERO_FACTURE, numero);
		noter(confiances, ChampFacture.DATE_EMISSION, date);
		noter(confiances, ChampFacture.MONTANT_HT, montants.ht);
		noter(confiances, ChampFacture.MONTANT_TVA, montants.tva);
		noter(confiances, ChampFacture.TAUX_TVA, taux);
		noter(confiances, ChampFacture.MONTANT_TTC, montants.ttc);
		noter(confiances, ChampFacture.COMPTE_CONTREPARTIE, compte);

		Set<ChampFacture> aVerifier = EnumSet.noneOf(ChampFacture.class);
		confiances.forEach((champ, confiance) -> {
			if (confiance < SEUIL) {
				aVerifier.add(champ);
			}
		});
		// RG5 : HT + TVA = TTC, sinon les trois montants sont « À vérifier ».
		if (montants.ht != null && montants.tva != null && montants.ttc != null && montants.ht.valeur()
				.add(montants.tva.valeur()).subtract(montants.ttc.valeur()).abs().compareTo(ECART_TOLERE) > 0) {
			aVerifier.addAll(List.of(ChampFacture.MONTANT_HT, ChampFacture.MONTANT_TVA, ChampFacture.MONTANT_TTC));
		}
		return new ResultatLecture(true, client ? CLIENT : FOURNISSEUR, valeur(tiers), valeur(numero), valeur(date),
				valeur(montants.ht), valeur(montants.tva), valeur(taux), valeur(montants.ttc), valeur(compte),
				confiances, aVerifier, plusieursTaux ? detail : List.of());
	}

	private static <T> void noter(Map<ChampFacture, Double> confiances, ChampFacture champ, Lu<T> lu) {
		if (lu != null) {
			confiances.put(champ, lu.confiance());
		}
	}

	private static <T> T valeur(Lu<T> lu) {
		return lu == null ? null : lu.valeur();
	}

	// ---- Montants HT, TVA et TTC : dernier montant de la ligne qui porte le libellé.

	private static final class Montants {
		private Lu<BigDecimal> ht;
		private Lu<BigDecimal> tva;
		private Lu<BigDecimal> ttc;
	}

	private static Montants montants(List<String> lignes) {
		Montants trouves = new Montants();
		Lu<BigDecimal> plusGrand = null;
		for (String ligne : lignes) {
			List<BigDecimal> valeurs = montantsDe(ligne);
			if (!valeurs.isEmpty()) {
				ranger(trouves, sansAccents(ligne.toLowerCase(Locale.ROOT)), valeurs.get(valeurs.size() - 1));
				plusGrand = plusGrand(plusGrand, valeurs);
			}
		}
		if (trouves.ttc == null && trouves.ht == null && trouves.tva == null) {
			trouves.ttc = plusGrand;
		}
		return trouves;
	}

	// Dernier montant de la ligne rangé en TTC, HT ou TVA selon les mots de la ligne.
	private static void ranger(Montants trouves, String bas, BigDecimal dernier) {
		boolean total = bas.contains("total") || bas.contains("montant");
		if (mot(bas, "ttc") || bas.contains("net a payer") || bas.contains("total a payer")) {
			trouves.ttc = meilleur(trouves.ttc, new Lu<>(dernier, SUR));
		} else if (mot(bas, "ht") || bas.contains("hors taxe")) {
			trouves.ht = meilleur(trouves.ht, new Lu<>(dernier, total ? SUR : PROBABLE));
		} else if (mot(bas, "tva")) {
			boolean sur = total || POURCENTAGE.matcher(bas).find();
			trouves.tva = meilleur(trouves.tva, new Lu<>(dernier, sur ? SUR : PROBABLE));
		} else if (total) {
			// Ticket : « TOTAL 12,50 » sans « TTC ».
			trouves.ttc = meilleur(trouves.ttc, new Lu<>(dernier, DEVINE));
		}
	}

	// Plus grand montant vu (TTC de secours quand aucun total n'est annoncé).
	private static Lu<BigDecimal> plusGrand(Lu<BigDecimal> actuel, List<BigDecimal> valeurs) {
		Lu<BigDecimal> plusGrand = actuel;
		for (BigDecimal valeur : valeurs) {
			if (plusGrand == null || valeur.compareTo(plusGrand.valeur()) > 0) {
				plusGrand = new Lu<>(valeur, FAIBLE);
			}
		}
		return plusGrand;
	}

	// Le plus sûr ; à confiance égale, le dernier (les totaux sont en bas de la facture).
	private static <T> Lu<T> meilleur(Lu<T> actuel, Lu<T> candidat) {
		return actuel == null || candidat.confiance() >= actuel.confiance() ? candidat : actuel;
	}

	static List<BigDecimal> montantsDe(String ligne) {
		List<BigDecimal> valeurs = new ArrayList<>();
		int debut = 0;
		while (debut < ligne.length()) {
			int fin = finDeMontant(ligne, debut);
			if (fin < 0) {
				debut++;
			} else {
				String entier = ligne.substring(debut, fin - 3).replace(" ", "").replace(".", "");
				valeurs.add(new BigDecimal(entier + "." + ligne.substring(fin - 2, fin)));
				debut = fin;
			}
		}
		return valeurs;
	}

	// Fin du montant qui commence à « debut » (chiffres, groupes de milliers, 2 décimales), -1 sinon.
	private static int finDeMontant(String ligne, int debut) {
		if (!chiffre(ligne, debut) || parmi(ligne, debut - 1, CHIFFRES_ET_SEPARATEURS)) {
			return -1;
		}
		int i = debut;
		while (chiffre(ligne, i)) {
			i++;
		}
		while (parmi(ligne, i, " .") && chiffre(ligne, i + 1) && chiffre(ligne, i + 2) && chiffre(ligne, i + 3)) {
			i += 4;
		}
		if (!parmi(ligne, i, ",.") || !chiffre(ligne, i + 1) || !chiffre(ligne, i + 2)) {
			return -1;
		}
		int fin = i + 3;
		return parmi(ligne, fin, CHIFFRES_ET_SEPARATEURS) || suiviDePourcent(ligne, fin) ? -1 : fin;
	}

	private static boolean suiviDePourcent(String ligne, int position) {
		int i = position;
		while (i < ligne.length() && Character.isWhitespace(ligne.charAt(i))) {
			i++;
		}
		return parmi(ligne, i, "%");
	}

	private static boolean chiffre(String ligne, int i) {
		return i >= 0 && i < ligne.length() && ligne.charAt(i) >= '0' && ligne.charAt(i) <= '9';
	}

	private static boolean parmi(String ligne, int i, String caracteres) {
		return i >= 0 && i < ligne.length() && caracteres.indexOf(ligne.charAt(i)) >= 0;
	}

	private static boolean mot(String texte, String mot) {
		return Pattern.compile("(?<![a-z])" + mot + "(?![a-z])").matcher(texte).find();
	}

	// ---- Taux de TVA : pourcentage de la ligne TVA, sinon calculé depuis HT et TVA.

	private static Lu<BigDecimal> taux(List<String> lignes, Montants montants) {
		Lu<BigDecimal> ailleurs = null;
		for (String ligne : lignes) {
			Matcher m = POURCENTAGE.matcher(ligne);
			if (m.find()) {
				BigDecimal taux = new BigDecimal(m.group(1).replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
				if (mot(sansAccents(ligne.toLowerCase(Locale.ROOT)), "tva")) {
					return new Lu<>(taux, SUR);
				}
				ailleurs = ailleurs == null ? new Lu<>(taux, DEVINE) : ailleurs;
			}
		}
		return ailleurs != null ? ailleurs : tauxCalcule(montants);
	}

	// Taux connu le plus proche de TVA / HT (à 0,5 point près), sinon vide.
	private static Lu<BigDecimal> tauxCalcule(Montants montants) {
		if (montants.ht == null || montants.tva == null || montants.ht.valeur().signum() <= 0) {
			return null;
		}
		BigDecimal calcule = montants.tva.valeur().multiply(CENT).divide(montants.ht.valeur(), 2, RoundingMode.HALF_UP);
		for (BigDecimal connu : TAUX_CONNUS) {
			if (calcule.subtract(connu).abs().compareTo(new BigDecimal("0.5")) <= 0) {
				return new Lu<>(connu, DEVINE);
			}
		}
		return null;
	}

	// ---- US-54 CA2 : TVA par taux. Ligne avec un taux et un couple base / TVA cohérent (base x taux
	// = TVA), ou ligne « TVA x % » avec un seul montant (TVA sans base). Premier détail trouvé par taux.

	static List<TvaParTaux> detailTva(List<String> lignes) {
		Map<BigDecimal, TvaParTaux> parTaux = new LinkedHashMap<>();
		for (String ligne : lignes) {
			Matcher pourcentage = POURCENTAGE.matcher(ligne);
			if (!pourcentage.find()) {
				continue;
			}
			BigDecimal taux = new BigDecimal(pourcentage.group(1).replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
			if (taux.signum() <= 0 || taux.compareTo(TAUX_MAX) > 0 || parTaux.containsKey(taux)) {
				continue;
			}
			List<BigDecimal> valeurs = montantsDe(ligne);
			Optional<TvaParTaux> lue = baseEtTva(taux, valeurs);
			if (lue.isEmpty() && valeurs.size() == 1 && mot(sansAccents(ligne.toLowerCase(Locale.ROOT)), "tva")) {
				lue = Optional.of(new TvaParTaux(taux, null, valeurs.get(0)));
			}
			lue.ifPresent(tva -> parTaux.put(taux, tva));
		}
		return parTaux.values().stream().sorted((a, b) -> b.taux().compareTo(a.taux())).toList();
	}

	private static Optional<TvaParTaux> baseEtTva(BigDecimal taux, List<BigDecimal> valeurs) {
		for (int i = 0; i < valeurs.size(); i++) {
			BigDecimal attendue = valeurs.get(i).multiply(taux).divide(CENT, 2, RoundingMode.HALF_UP);
			for (int j = i + 1; j < valeurs.size(); j++) {
				if (attendue.subtract(valeurs.get(j)).abs().compareTo(ECART_TAUX) <= 0) {
					return Optional.of(new TvaParTaux(taux, valeurs.get(i), valeurs.get(j)));
				}
			}
		}
		return Optional.empty();
	}

	// Taux « Multiple » (valeur vide) : sûr si la TVA des taux fait la TVA totale, sinon « À vérifier ».
	private static Lu<BigDecimal> tauxMultiple(List<TvaParTaux> detail, Montants montants) {
		BigDecimal somme = detail.stream().map(TvaParTaux::montantTva).reduce(BigDecimal.ZERO, BigDecimal::add);
		boolean coherent = montants.tva != null
				&& somme.subtract(montants.tva.valeur()).abs().compareTo(ECART_TOLERE) <= 0;
		return new Lu<>(null, coherent ? SUR : DEVINE);
	}

	// ---- US-54 RG3 : destinataire d'une facture client, jamais l'entreprise du dossier ; sinon vide.

	private static Lu<String> destinataire(List<String> lignes, String entrepriseDuDossier) {
		String entreprise = entrepriseDuDossier == null ? ""
				: sansAccents(entrepriseDuDossier.toLowerCase(Locale.ROOT)).strip();
		for (int i = 0; i < lignes.size(); i++) {
			String ligne = lignes.get(i);
			Matcher m = DESTINATAIRE_ANNONCE.matcher(sansAccents(ligne.toLowerCase(Locale.ROOT)));
			if (!m.matches()) {
				continue;
			}
			// Nom sur la même ligne (« Client : Mairie de Lyon ») ou sur la suivante (« Facturé à : »).
			String nom = m.group(1).isBlank() && i + 1 < lignes.size() ? lignes.get(i + 1)
					: ligne.substring(Math.min(m.start(1), ligne.length()));
			nom = nom.strip();
			String nomBas = sansAccents(nom.toLowerCase(Locale.ROOT));
			boolean unNom = nom.length() >= 2 && nomBas.chars().anyMatch(Character::isLetter)
					&& !NUMERO_SEUL.matcher(nomBas).matches();
			if (unNom && (entreprise.isEmpty() || !nomBas.contains(entreprise))) {
				return new Lu<>(nom, PROBABLE);
			}
		}
		return null;
	}

	// ---- Tiers (RG3) : fournisseur connu, sinon nom annoncé (« Fournisseur : ... »), sinon vide.

	private static Lu<String> tiers(List<String> lignes, String tout) {
		String premier = null;
		int position = Integer.MAX_VALUE;
		for (Map.Entry<String, String> connu : FOURNISSEURS_CONNUS.entrySet()) {
			Matcher m = Pattern.compile("(?<![a-z])" + Pattern.quote(connu.getValue()) + "(?![a-z])").matcher(tout);
			if (m.find() && m.start() < position) {
				position = m.start();
				premier = connu.getKey();
			}
		}
		if (premier != null) {
			return new Lu<>(premier, SUR);
		}
		for (String ligne : lignes) {
			Matcher m = TIERS_ANNONCE.matcher(sansAccents(ligne.toLowerCase(Locale.ROOT)));
			if (m.find()) {
				// Même position dans la ligne d'origine : le nom garde ses majuscules et ses accents.
				String nom = ligne.substring(Math.min(m.start(1), ligne.length())).strip();
				return nom.isEmpty() ? null : new Lu<>(nom, PROBABLE);
			}
		}
		return null;
	}

	// ---- Numéro de facture.

	private static Lu<String> numero(String tout) {
		Optional<String> annonce = premierAvecChiffre(NUMERO_FACTURE.matcher(tout));
		if (annonce.isPresent()) {
			return new Lu<>(annonce.get(), SUR);
		}
		return premierAvecChiffre(NUMERO.matcher(tout)).map(n -> new Lu<>(n, DEVINE)).orElse(null);
	}

	// « FA-2026-001. » -> « FA-2026-001 » (sans expression régulière : pas de risque de lenteur).
	private static String sansSeparateursFinaux(String numero) {
		int fin = numero.length();
		while (fin > 0 && ".-_/".indexOf(numero.charAt(fin - 1)) >= 0) {
			fin--;
		}
		return numero.substring(0, fin);
	}

	private static Optional<String> premierAvecChiffre(Matcher m) {
		while (m.find()) {
			String numero = sansSeparateursFinaux(m.group(1)).toUpperCase(Locale.ROOT);
			if (numero.chars().anyMatch(Character::isDigit) && numero.length() >= 3) {
				return Optional.of(numero);
			}
		}
		return Optional.empty();
	}

	// ---- Date d'émission : date de la ligne « Facture … du / Date … », sinon la première date.

	private static Lu<LocalDate> date(List<String> lignes) {
		Lu<LocalDate> premiere = null;
		for (String ligne : lignes) {
			String bas = sansAccents(ligne.toLowerCase(Locale.ROOT));
			Optional<LocalDate> date = dateDe(bas);
			if (date.isEmpty()) {
				continue;
			}
			if (bas.contains("facture") || bas.contains("date") || bas.contains("emis")) {
				return new Lu<>(date.get(), SUR);
			}
			premiere = premiere == null ? new Lu<>(date.get(), DEVINE) : premiere;
		}
		return premiere;
	}

	static Optional<LocalDate> dateDe(String bas) {
		Matcher chiffres = DATE_CHIFFRES.matcher(bas);
		while (chiffres.find()) {
			int annee = Integer.parseInt(chiffres.group(3));
			Optional<LocalDate> date = date(annee < 100 ? 2000 + annee : annee, Integer.parseInt(chiffres.group(2)),
					Integer.parseInt(chiffres.group(1)));
			if (date.isPresent()) {
				return date;
			}
		}
		Matcher lettres = DATE_LETTRES.matcher(bas);
		if (lettres.find()) {
			return date(Integer.parseInt(lettres.group(3)), MOIS.indexOf(lettres.group(2)) + 1,
					Integer.parseInt(lettres.group(1)));
		}
		return Optional.empty();
	}

	private static Optional<LocalDate> date(int annee, int mois, int jour) {
		try {
			return Optional.of(LocalDate.of(annee, mois, jour));
		} catch (DateTimeException e) {
			return Optional.empty();
		}
	}

	// ---- Contrepartie : compte proposé selon la dépense (US-53) ou la vente (US-54), toujours « À vérifier ».

	private static Lu<String> compte(String tout, Map<String, String> comptesParMot, String parDefaut) {
		for (Map.Entry<String, String> regle : comptesParMot.entrySet()) {
			if (mot(tout, Pattern.quote(regle.getKey()))) {
				return new Lu<>(regle.getValue(), DEVINE);
			}
		}
		return new Lu<>(parDefaut, FAIBLE);
	}

	static String sansAccents(String texte) {
		return Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
	}

}
