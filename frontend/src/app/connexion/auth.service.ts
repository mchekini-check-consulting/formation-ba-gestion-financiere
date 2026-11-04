import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

export type Role = 'COLLABORATEUR' | 'DIRIGEANT';

export interface ConnexionReponse {
  role: Role;
  /** Écran suivant : le code SMS (US-04). */
  etapeSuivante: 'CODE_SMS';
}

/** US-03 Se connecter : POST /auth/connexion du backend (via le gateway). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase()}/auth/connexion`;

  seConnecter(email: string, motDePasse: string): Observable<ConnexionReponse> {
    return this.http.post<ConnexionReponse>(this.url, { email, motDePasse });
  }
}
