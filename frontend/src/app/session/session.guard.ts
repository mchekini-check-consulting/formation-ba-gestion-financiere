import { HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { CanActivateChildFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from '../connexion/auth.service';
import { SessionService, accueil } from './session.service';

/**
 * Pages réservées aux personnes connectées (US-07) : à chaque page ouverte, le backend
 * confirme que la session est active. Sinon retour à la connexion, avec « Votre session
 * a expiré » si c'est le cas (CA2 : Jean, inactif depuis 30 minutes, clique sur un dossier).
 */
export const sessionGuard: CanActivateChildFn = (route) => {
  const auth = inject(AuthService);
  const session = inject(SessionService);
  const router = inject(Router);

  if (!session.jeton()) {
    return router.createUrlTree(['/connexion']);
  }
  return auth.session().pipe(
    map((utilisateur) => {
      session.mettreAJour(utilisateur);
      const roleAttendu = route.data['role'] as string | undefined;
      return roleAttendu && roleAttendu !== utilisateur.role
        ? router.createUrlTree([accueil(utilisateur.role)])
        : true;
    }),
    catchError((erreur: HttpErrorResponse) => {
      session.fermer();
      const expiree = erreur.error?.code === 'SESSION_EXPIREE';
      return of(router.createUrlTree(['/connexion'], expiree ? { queryParams: { raison: 'session-expiree' } } : {}));
    }),
  );
};
