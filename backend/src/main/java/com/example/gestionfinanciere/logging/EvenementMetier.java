package com.example.gestionfinanciere.logging;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * PTO-84 : log métier d'une action clé (création de cabinet, connexion, invitation, dossier…),
 * une ligne JSON avec l'action (event), son résultat (outcome) et des identifiants techniques.
 * Jamais de donnée personnelle : seulement des identifiants, des codes de résultat et des noms
 * de champs (jamais leurs valeurs).
 *
 * <pre>
 * EvenementMetier.de("dossier.creation", resultat.statut()).avec(compte.identifiantsPourLogs()).info();
 * </pre>
 */
public final class EvenementMetier {

	private static final Logger log = LoggerFactory.getLogger(EvenementMetier.class);

	private final String event;
	private final String outcome;
	private final Map<String, String> champs = new LinkedHashMap<>();
	private String couche = "service";

	private EvenementMetier(String event, String outcome) {
		this.event = event;
		this.outcome = outcome;
	}

	public static EvenementMetier de(String event, String outcome) {
		return new EvenementMetier(event, outcome);
	}

	// Résultat repris d'un statut : CABINET_CREE -> « cabinet_cree ».
	public static EvenementMetier de(String event, Enum<?> statut) {
		return new EvenementMetier(event, statut.name().toLowerCase(Locale.ROOT));
	}

	// Champ ignoré si la valeur est absente.
	public EvenementMetier avec(String champ, Object valeur) {
		if (valeur != null) {
			champs.put(champ, String.valueOf(valeur));
		}
		return this;
	}

	public EvenementMetier avec(Map<String, ?> valeurs) {
		valeurs.forEach(this::avec);
		return this;
	}

	// Noms des champs refusés d'un formulaire, triés (« email,siren ») : jamais les valeurs saisies.
	public EvenementMetier avecChampsInvalides(Map<String, ?> erreurs) {
		if (!erreurs.isEmpty()) {
			champs.put("champsInvalides", String.join(",", new TreeSet<>(erreurs.keySet())));
		}
		return this;
	}

	// Événement écrit depuis un contrôleur (ex. accès refusé).
	public EvenementMetier depuisLeControleur() {
		this.couche = "controller";
		return this;
	}

	public void info() {
		ecrire(Level.INFO);
	}

	// Refus métier : formulaire invalide, doublon, code faux…
	public void warn() {
		ecrire(Level.WARN);
	}

	// Panne : email impossible à envoyer…
	public void error() {
		ecrire(Level.ERROR);
	}

	// info si l'action a abouti, warn sinon.
	public void journaliser(boolean reussi) {
		ecrire(reussi ? Level.INFO : Level.WARN);
	}

	private void ecrire(Level niveau) {
		MdcContext ctx = MdcContext.open().with("layer", couche).with("event", event).with("outcome", outcome);
		champs.forEach(ctx::with);
		try {
			log.atLevel(niveau).log(event + " : " + outcome);
		} finally {
			ctx.close();
		}
	}

}
