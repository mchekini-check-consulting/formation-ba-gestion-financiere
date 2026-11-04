import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { apiBase } from '../api';
import { SessionService } from './session.service';

/** Ajoute le jeton de session aux appels du backend du projet (jamais aux autres sites). */
export const authInterceptor: HttpInterceptorFn = (requete, suivant) => {
  const jeton = inject(SessionService).jeton();
  if (!jeton || requete.headers.has('Authorization') || !requete.url.startsWith(apiBase())) {
    return suivant(requete);
  }
  return suivant(requete.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } }));
};
