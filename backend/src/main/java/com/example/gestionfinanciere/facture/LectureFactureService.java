package com.example.gestionfinanciere.facture;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.facture.lecture.ResultatLecture;
import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.Loggable;

/**
 * US-53 Lecture automatique d'une facture fournisseur, côté base de données : le fichier à lire,
 * puis le résultat (champs, niveaux de confiance, statut « Lue » ou « Lecture échouée »). La lecture
 * elle-même (PDF, OCR) se fait entre les deux, hors transaction (LanceurLecture). Jamais le
 * contenu du fichier ni les valeurs lues dans les logs.
 */
@Service
@Loggable
public class LectureFactureService {

	private static final String EVENEMENT = "facture.lecture";
	private static final String CHAMP_FACTURE = "factureId";

	private final FactureRepository factures;
	private final ChampLuRepository champs;
	private final Clock horloge;

	public LectureFactureService(FactureRepository factures, ChampLuRepository champs, Clock horloge) {
		this.factures = factures;
		this.champs = champs;
		this.horloge = horloge;
	}

	// La facture en attente de lecture ; vide si elle a été supprimée ou déjà lue.
	@Transactional(readOnly = true)
	public Optional<FactureALire> factureALire(Long factureId) {
		return factures.findById(factureId)
				.filter(f -> f.getLectureStatut() == Facture.StatutLecture.EN_COURS && f.getContenu() != null)
				.map(FactureALire::de);
	}

	// RG2 à RG6 : enregistre le résultat ; chaque champ lu garde son niveau de confiance (RG4).
	@Transactional
	public void enregistrer(Long factureId, ResultatLecture resultat, Duration duree) {
		Optional<Facture> trouvee = factures.findById(factureId);
		if (trouvee.isEmpty()) {
			EvenementMetier.de(EVENEMENT, "facture_supprimee").avec(CHAMP_FACTURE, factureId).info();
			return;
		}
		Facture facture = trouvee.get();
		facture.terminerLecture(resultat, horloge.instant());
		champs.supprimerDeLaFacture(factureId);
		List<ChampLu> lus = resultat.confiances().entrySet().stream().sorted(Map.Entry.comparingByKey())
				.map(c -> new ChampLu(facture, c.getKey(), c.getValue(), resultat.aVerifier().contains(c.getKey())))
				.toList();
		champs.saveAll(lus);
		EvenementMetier evenement = EvenementMetier.de(EVENEMENT, facture.getLectureStatut())
				.avec(CHAMP_FACTURE, factureId).avec("dossierId", facture.getDossier().getId())
				.avec("dureeMs", duree.toMillis());
		evenement.avec("sens", facture.getSens());
		if (resultat.lisible()) {
			evenement.avec("aVerifier", resultat.aVerifier().size()).avec("aRenseigner", resultat.aRenseigner().size())
					.avec("tauxMultiple", resultat.tauxMultiple());
		}
		evenement.journaliser(resultat.lisible());
	}

}
