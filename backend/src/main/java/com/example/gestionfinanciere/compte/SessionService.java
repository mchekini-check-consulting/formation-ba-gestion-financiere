package com.example.gestionfinanciere.compte;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.gestionfinanciere.logging.Loggable;

// US-07 Terminer sa session : déconnexion et expiration après 30 minutes sans activité.
@Service
@Loggable
public class SessionService {

	static final Duration INACTIVITE_MAX = Duration.ofMinutes(30);

	private final SessionRepository sessions;
	private final Clock horloge;

	public SessionService(SessionRepository sessions, Clock horloge) {
		this.sessions = sessions;
		this.horloge = horloge;
	}

	// Renvoie le jeton de session, à présenter ensuite dans l'en-tête Authorization.
	@Transactional
	public String ouvrir(Compte compte) {
		String jeton = Jetons.nouveau();
		sessions.save(new SessionUtilisateur(compte, Jetons.empreinte(jeton), horloge.instant()));
		return jeton;
	}

	// Chaque requête avec une session active compte comme une activité.
	@Transactional
	public ResultatSession consulter(String jeton) {
		Optional<SessionUtilisateur> trouvee = trouver(jeton);
		if (trouvee.isEmpty()) {
			return ResultatSession.nonConnecte();
		}
		SessionUtilisateur session = trouvee.get();
		Instant maintenant = horloge.instant();
		if (!maintenant.isBefore(session.getDerniereActivite().plus(INACTIVITE_MAX))) {
			session.fermer(); // RG2 : 30 minutes sans activité
			return ResultatSession.expiree();
		}
		session.enregistrerActivite(maintenant);
		return ResultatSession.active(session.getCompte());
	}

	// RG1 : « Se déconnecter » ferme la session.
	@Transactional
	public void fermer(String jeton) {
		trouver(jeton).ifPresent(SessionUtilisateur::fermer);
	}

	// US-06 : après un changement de mot de passe, les autres sessions sont fermées.
	@Transactional
	public void fermerToutes(Compte compte) {
		sessions.findByCompteAndFermeeFalse(compte).forEach(SessionUtilisateur::fermer);
	}

	private Optional<SessionUtilisateur> trouver(String jeton) {
		if (ConnexionService.estVide(jeton)) {
			return Optional.empty();
		}
		return sessions.findByEmpreinteJeton(Jetons.empreinte(jeton)).filter(s -> !s.isFermee());
	}

}
