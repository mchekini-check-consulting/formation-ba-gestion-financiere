package com.example.gestionfinanciere.banque;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.gestionfinanciere.dossier.Dossier;

/**
 * US-58 Bouchon bancaire : transactions de démonstration, sans connexion à un vrai compte.
 * Activé seulement si pointeo.bouchon-bancaire vaut true (POINTEO_BOUCHON_BANCAIRE, helm.yaml :
 * dev et qualif ; jamais en preprod ni en prod). Désactivé, cette classe n'existe pas dans
 * l'application : aucune donnée de test ne peut apparaître.
 *
 * Les données viennent du fichier src/main/resources/bouchon/transactions.csv (son en-tête
 * explique comment en ajouter). Les dates y sont écrites en « il y a N jours » : le jeu de test
 * couvre toujours les 3 derniers mois. Même jeu pour chaque dossier, toujours marqué « Bouchon ».
 */
@Component
@ConditionalOnProperty(name = "pointeo.bouchon-bancaire", havingValue = "true")
public class BouchonTransactions implements FournisseurTransactions {

	static final String FICHIER = "/bouchon/transactions.csv";
	private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
	private static final String SEPARATEUR = ";";
	private static final int COLONNES = 5;

	// Une ligne du fichier : la date est calculée au moment de l'appel.
	record LigneBouchon(int ilYaJours, String libelle, String tiers, BigDecimal montant, Transaction.Sens sens) {
	}

	private final List<LigneBouchon> lignes;
	private final Clock horloge;

	public BouchonTransactions(Clock horloge) {
		this.horloge = horloge;
		this.lignes = lire(ouvrir(FICHIER));
	}

	@Override
	public List<Transaction> transactions(Dossier dossier) {
		LocalDate aujourdhui = LocalDate.ofInstant(horloge.instant(), PARIS);
		return lignes.stream().map(ligne -> new Transaction(aujourdhui.minusDays(ligne.ilYaJours()), ligne.libelle(),
				ligne.tiers(), ligne.montant(), ligne.sens(), Transaction.Source.BOUCHON)).toList();
	}

	static InputStream ouvrir(String fichier) {
		InputStream flux = BouchonTransactions.class.getResourceAsStream(fichier);
		if (flux == null) {
			throw new IllegalStateException("fichier du bouchon bancaire absent : " + fichier);
		}
		return flux;
	}

	/**
	 * Lit le fichier : lignes vides et lignes commençant par # ignorées, puis l'en-tête des
	 * colonnes, puis une transaction par ligne. Une ligne mal écrite arrête le démarrage avec son
	 * numéro, pour qu'une erreur de saisie se voie tout de suite.
	 */
	static List<LigneBouchon> lire(InputStream flux) {
		List<LigneBouchon> lignes = new ArrayList<>();
		try (BufferedReader lecteur = new BufferedReader(new InputStreamReader(flux, StandardCharsets.UTF_8))) {
			boolean enTeteLu = false;
			int numero = 0;
			for (String texte = lecteur.readLine(); texte != null; texte = lecteur.readLine()) {
				numero++;
				String ligne = texte.strip();
				if (ligne.isEmpty() || ligne.startsWith("#")) {
					continue;
				}
				if (enTeteLu) {
					lignes.add(ligne(ligne, numero));
				} else {
					enTeteLu = true;
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return List.copyOf(lignes);
	}

	private static LigneBouchon ligne(String ligne, int numero) {
		String[] colonnes = ligne.split(SEPARATEUR, -1);
		if (colonnes.length != COLONNES) {
			throw erreur(numero, COLONNES + " colonnes attendues séparées par « ; »");
		}
		int ilYaJours;
		BigDecimal montant;
		Transaction.Sens sens;
		try {
			ilYaJours = Integer.parseInt(colonnes[0].strip());
			montant = new BigDecimal(colonnes[3].strip().replace(',', '.'));
			sens = Transaction.Sens.valueOf(colonnes[4].strip().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw erreur(numero, "valeur incorrecte (" + e.getMessage() + ")");
		}
		String libelle = colonnes[1].strip();
		if (ilYaJours < 0 || montant.signum() <= 0 || libelle.isEmpty()) {
			throw erreur(numero, "nombre de jours positif, montant supérieur à 0 et libellé obligatoires");
		}
		return new LigneBouchon(ilYaJours, libelle, colonnes[2].strip(), montant, sens);
	}

	private static IllegalArgumentException erreur(int numero, String raison) {
		return new IllegalArgumentException("bouchon bancaire, ligne " + numero + " : " + raison);
	}

}
