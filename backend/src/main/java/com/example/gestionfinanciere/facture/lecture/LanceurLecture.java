package com.example.gestionfinanciere.facture.lecture;

import java.time.Duration;
import java.util.Optional;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.gestionfinanciere.facture.FichierFacture;
import com.example.gestionfinanciere.facture.LectureFactureService;

/**
 * US-53 RG1 et RG6 : lecture en arrière-plan (asynchrone), lancée une fois le dépôt enregistré.
 * La personne qui dépose n'attend pas : la facture reste « Lecture en cours » jusqu'au résultat.
 * Toute erreur pendant la lecture : « Lecture échouée » (CA4), jamais une lecture bloquée.
 */
@Component
public class LanceurLecture {

	private final LectureFactureService lectures;
	private final ExtracteurTexte extracteur;

	public LanceurLecture(LectureFactureService lectures, ExtracteurTexte extracteur) {
		this.lectures = lectures;
		this.extracteur = extracteur;
	}

	@Async(LectureConfiguration.EXECUTEUR)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void lancer(DemandeLecture demande) {
		lire(demande.factureId());
	}

	void lire(Long factureId) {
		Optional<FichierFacture> fichier = lectures.fichierALire(factureId);
		if (fichier.isEmpty()) {
			return;
		}
		long debut = System.nanoTime();
		ResultatLecture resultat;
		try {
			resultat = AnalyseurFacture.analyser(extracteur.texte(fichier.get().contenu(), fichier.get().typeFichier()));
		} catch (RuntimeException e) {
			resultat = ResultatLecture.illisible();
		}
		lectures.enregistrer(factureId, resultat, Duration.ofNanos(System.nanoTime() - debut));
	}

}
