package com.example.gestionfinanciere.facture;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * US-55 Contrôles de la fiche facture, sans accès à la base (le plan comptable, RG3, est vérifié par
 * FicheFactureService). Chaque contrôle renvoie le message affiché tel quel par l'écran, ou rien.
 */
final class ControleFiche {

	// RG5 / CA2 : un message par champ obligatoire, dans l'ordre de la fiche.
	static final String MESSAGE_TIERS = "Indiquez le tiers";
	static final String MESSAGE_NUMERO = "Indiquez le numéro de facture";
	static final String MESSAGE_DATE = "Indiquez la date d'émission";
	static final String MESSAGE_HT = "Indiquez le montant HT";
	static final String MESSAGE_TVA = "Indiquez le montant de TVA";
	static final String MESSAGE_TTC = "Indiquez le montant TTC";
	static final String MESSAGE_CONTREPARTIE = "Indiquez le compte de contrepartie";

	// RG4 / CA3.
	static final String MESSAGE_INCOHERENT = "Montants incohérents : HT + TVA ≠ TTC";

	// Valeurs impossibles à enregistrer (types et tailles des colonnes de V23__lecture_facture.sql).
	static final String MESSAGE_TYPE_TIERS = "Type de tiers inconnu : choisissez Fournisseur ou Client";
	static final String MESSAGE_NOM_TROP_LONG = "Nom du tiers trop long : 255 caractères maximum";
	static final String MESSAGE_NUMERO_TROP_LONG = "Numéro de facture trop long : 100 caractères maximum";
	static final String MESSAGE_COMPTE_TROP_LONG = "Compte de contrepartie trop long : 20 caractères maximum";
	static final String MESSAGE_DECIMALES = "%s : 2 décimales au maximum";
	static final String MESSAGE_MONTANT_TROP_GRAND = "%s : montant trop grand";
	static final String MESSAGE_TAUX = "Taux de TVA : entre 0 et 100 %";

	private static final Set<String> TYPES_TIERS = Set.of("FOURNISSEUR", "CLIENT");
	private static final int NOM_MAX = 255;
	private static final int NUMERO_MAX = 100;
	private static final int COMPTE_MAX = 20;
	// numeric(12, 2) : au plus 10 chiffres avant la virgule.
	private static final BigDecimal MONTANT_MAX = new BigDecimal("10000000000");
	private static final BigDecimal CENT = new BigDecimal("100");

	private ControleFiche() {
	}

	// Valeurs enregistrables, pour « Enregistrer » comme pour « Valider » ; les champs vides sont acceptés.
	static Optional<String> format(InformationsFacture infos) {
		if (infos.typeTiers() != null && !TYPES_TIERS.contains(infos.typeTiers())) {
			return Optional.of(MESSAGE_TYPE_TIERS);
		}
		if (tropLong(infos.nomTiers(), NOM_MAX)) {
			return Optional.of(MESSAGE_NOM_TROP_LONG);
		}
		if (tropLong(infos.numeroFacture(), NUMERO_MAX)) {
			return Optional.of(MESSAGE_NUMERO_TROP_LONG);
		}
		if (tropLong(infos.compteContrepartie(), COMPTE_MAX)) {
			return Optional.of(MESSAGE_COMPTE_TROP_LONG);
		}
		return montant("Montant HT", infos.montantHt()).or(() -> montant("Montant TVA", infos.montantTva()))
				.or(() -> montant("Montant TTC", infos.montantTtc())).or(() -> taux(infos.tauxTva()));
	}

	// RG5 : champs obligatoires pour valider ; le premier manquant est nommé.
	static Optional<String> obligatoires(InformationsFacture infos) {
		List<Object[]> champs = List.of(new Object[] { infos.nomTiers(), MESSAGE_TIERS },
				new Object[] { infos.numeroFacture(), MESSAGE_NUMERO },
				new Object[] { infos.dateEmission(), MESSAGE_DATE }, new Object[] { infos.montantHt(), MESSAGE_HT },
				new Object[] { infos.montantTva(), MESSAGE_TVA }, new Object[] { infos.montantTtc(), MESSAGE_TTC },
				new Object[] { infos.compteContrepartie(), MESSAGE_CONTREPARTIE });
		return champs.stream().filter(champ -> champ[0] == null).map(champ -> (String) champ[1]).findFirst();
	}

	// RG4 : HT + TVA = TTC, au centime près (montants obligatoires déjà présents).
	static Optional<String> coherence(InformationsFacture infos) {
		if (infos.montantHt().add(infos.montantTva()).compareTo(infos.montantTtc()) != 0) {
			return Optional.of(MESSAGE_INCOHERENT);
		}
		return Optional.empty();
	}

	private static boolean tropLong(String valeur, int max) {
		return valeur != null && valeur.length() > max;
	}

	private static Optional<String> montant(String libelle, BigDecimal valeur) {
		if (valeur == null) {
			return Optional.empty();
		}
		if (valeur.stripTrailingZeros().scale() > 2) {
			return Optional.of(MESSAGE_DECIMALES.formatted(libelle));
		}
		if (valeur.abs().compareTo(MONTANT_MAX) >= 0) {
			return Optional.of(MESSAGE_MONTANT_TROP_GRAND.formatted(libelle));
		}
		return Optional.empty();
	}

	private static Optional<String> taux(BigDecimal valeur) {
		if (valeur == null) {
			return Optional.empty();
		}
		if (valeur.signum() < 0 || valeur.compareTo(CENT) > 0) {
			return Optional.of(MESSAGE_TAUX);
		}
		if (valeur.stripTrailingZeros().scale() > 2) {
			return Optional.of(MESSAGE_DECIMALES.formatted("Taux de TVA"));
		}
		return Optional.empty();
	}

}
