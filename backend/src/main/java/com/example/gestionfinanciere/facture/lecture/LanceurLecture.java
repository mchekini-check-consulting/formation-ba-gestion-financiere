package com.example.gestionfinanciere.facture.lecture;

import java.time.Duration;
import java.util.Optional;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.gestionfinanciere.facture.Facture;
import com.example.gestionfinanciere.facture.FactureALire;
import com.example.gestionfinanciere.facture.FichierFacture;
import com.example.gestionfinanciere.facture.LectureFactureService;

/**
 * US-53 RG1 et RG6, US-54 RG1 : lecture en arrière-plan (asynchrone), lancée une fois le dépôt enregistré.
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
		Optional<FactureALire> aLire = lectures.factureALire(factureId);
		if (aLire.isEmpty()) {
			return;
		}
		long debut = System.nanoTime();
		ResultatLecture resultat;
		try {
			FichierFacture fichier = aLire.get().fichier();
			String texte = extracteur.texte(fichier.contenu(), fichier.typeFichier());
			// US-54 : facture client, le tiers est le destinataire et la contrepartie un compte de produit.
			resultat = aLire.get().sens() == Facture.Sens.VENTE
					? AnalyseurFacture.analyserFactureClient(texte, aLire.get().entrepriseDuDossier())
					: AnalyseurFacture.analyser(texte);
		} catch (RuntimeException e) {
			resultat = ResultatLecture.illisible();
		}
		lectures.enregistrer(factureId, resultat, Duration.ofNanos(System.nanoTime() - debut));
	}

}
