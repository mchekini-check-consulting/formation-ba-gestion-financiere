package com.example.gestionfinanciere.compte;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.logging.EvenementMetier;
import com.example.gestionfinanciere.logging.Loggable;
import com.example.gestionfinanciere.notification.EnvoiEmail;

// US-06 Réinitialiser son mot de passe.
@Service
@Loggable
public class ReinitialisationService {

	static final Duration VALIDITE_LIEN = Duration.ofHours(1);
	static final String EVENEMENT_CHANGEMENT = "mot_de_passe.reinitialisation";

	private final CompteRepository comptes;
	private final JetonReinitialisationRepository jetons;
	private final SessionService sessions;
	private final PasswordEncoder encodeur;
	private final Clock horloge;
	private final EnvoiEmail emails;
	private final String frontendUrl;

	public ReinitialisationService(CompteRepository comptes, JetonReinitialisationRepository jetons,
			SessionService sessions, PasswordEncoder encodeur, Clock horloge, EnvoiEmail emails,
			@Value("${pointeo.frontend-url}") String frontendUrl) {
		this.comptes = comptes;
		this.jetons = jetons;
		this.sessions = sessions;
		this.encodeur = encodeur;
		this.horloge = horloge;
		this.emails = emails;
		this.frontendUrl = frontendUrl;
	}

	/**
	 * RG1 : envoie un lien valable 1 heure si un compte actif existe ; RG2 : l'appelant
	 * répond de la même façon dans tous les cas.
	 */
	@Transactional
	public void demander(String email) {
		if (ConnexionService.estVide(email)) {
			return;
		}
		Optional<Compte> compte = comptes.findByEmail(ConnexionService.normaliser(email)).filter(Compte::isActif);
		if (compte.isEmpty()) {
			EvenementMetier.de("mot_de_passe.lien_demande", "compte_inconnu_ou_inactif").warn();
			return;
		}
		String jeton = Jetons.nouveau();
		jetons.save(new JetonReinitialisation(compte.get(), Jetons.empreinte(jeton),
				horloge.instant().plus(VALIDITE_LIEN)));
		emails.envoyer(compte.get().getEmail(), "Pointéo : changer votre mot de passe",
				"Bonjour " + compte.get().getNomAffiche() + ",\n\n"
						+ "Pour choisir un nouveau mot de passe, ouvrez ce lien (valable 1 heure, une seule fois) :\n"
						+ frontendUrl + "/reinitialiser-mot-de-passe?jeton=" + jeton + "\n\n"
						+ "Si vous n'avez rien demandé, ignorez cet email.\n");
		EvenementMetier.de("mot_de_passe.lien_demande", "lien_envoye").avec(compte.get().identifiantsPourLogs())
				.info();
	}

	// CA2 : le lien ouvert est-il encore valable ?
	@Transactional(readOnly = true)
	public boolean lienValable(String jeton) {
		return trouverValable(jeton).isPresent();
	}

	@Transactional
	public ResultatReinitialisation reinitialiser(String jeton, String motDePasse) {
		Optional<JetonReinitialisation> trouve = trouverValable(jeton);
		if (trouve.isEmpty()) {
			EvenementMetier.de(EVENEMENT_CHANGEMENT, ResultatReinitialisation.Statut.LIEN_EXPIRE).warn();
			return ResultatReinitialisation.lienExpire();
		}
		List<String> regles = RegleMotDePasse.nonRespectees(motDePasse);
		if (!regles.isEmpty()) {
			EvenementMetier.de(EVENEMENT_CHANGEMENT, ResultatReinitialisation.Statut.TROP_FAIBLE)
					.avec(trouve.get().getCompte().identifiantsPourLogs()).avec("reglesNonRespectees", regles.size())
					.warn();
			return ResultatReinitialisation.tropFaible(regles); // CA3 : rien n'est changé
		}
		JetonReinitialisation lien = trouve.get();
		lien.utiliser(horloge.instant());
		Compte compte = lien.getCompte();
		compte.changerMotDePasse(encodeur.encode(motDePasse));
		sessions.fermerToutes(compte);
		EvenementMetier.de(EVENEMENT_CHANGEMENT, ResultatReinitialisation.Statut.CHANGE)
				.avec(compte.identifiantsPourLogs()).info();
		return ResultatReinitialisation.change();
	}

	private Optional<JetonReinitialisation> trouverValable(String jeton) {
		if (ConnexionService.estVide(jeton)) {
			return Optional.empty();
		}
		Instant maintenant = horloge.instant();
		return jetons.findByEmpreinteJeton(Jetons.empreinte(jeton)).filter(j -> j.estValable(maintenant));
	}

}
