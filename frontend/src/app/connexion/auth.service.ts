import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

export type Role = 'COLLABORATEUR' | 'DIRIGEANT';

/** US-03 : email + mot de passe corrects -> écran du code SMS (US-04). */
export interface ConnexionReponse {
  etapeSuivante: 'CODE_SMS';
  /** Identifie la tentative de connexion, à renvoyer avec le code. */
  defi: string;
  telephoneMasque: string;
  /** Mode test uniquement (aucun opérateur SMS branché). */
  codeDeTest: string | null;
}

/** US-04 : bon code -> session ouverte (US-07). */
export interface SessionOuverte {
  jeton: string;
  role: Role;
  nomAffiche: string;
  /** US-19 : administrateur du cabinet (menu « Équipe »). */
  administrateur?: boolean;
}

export interface CodeRenvoye {
  telephoneMasque: string;
  codeDeTest: string | null;
}

export interface SessionActive {
  nomAffiche: string;
  role: Role;
  administrateur?: boolean;
}

export interface DemandeEnvoyee {
  message: string;
  /** Mode test uniquement (aucune API d'email branchée). */
  jetonDeTest: string | null;
}

/** Appels d'authentification au backend (via le gateway) : US-03 à US-07. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase()}/auth`;

  seConnecter(email: string, motDePasse: string): Observable<ConnexionReponse> {
    return this.http.post<ConnexionReponse>(`${this.url}/connexion`, { email, motDePasse });
  }

  validerCode(defi: string, code: string): Observable<SessionOuverte> {
    return this.http.post<SessionOuverte>(`${this.url}/code`, { defi, code });
  }

  renvoyerCode(defi: string): Observable<CodeRenvoye> {
    return this.http.post<CodeRenvoye>(`${this.url}/code/renvoyer`, { defi });
  }

  /** Session encore active ? Compte aussi comme une activité côté serveur. */
  session(): Observable<SessionActive> {
    return this.http.get<SessionActive>(`${this.url}/session`);
  }

  /** Le jeton est passé explicitement : la session locale est effacée juste après. */
  deconnecter(jeton: string | null): Observable<void> {
    const headers = jeton ? new HttpHeaders({ Authorization: `Bearer ${jeton}` }) : undefined;
    return this.http.post<void>(`${this.url}/deconnexion`, {}, { headers });
  }

  demanderReinitialisation(email: string): Observable<DemandeEnvoyee> {
    return this.http.post<DemandeEnvoyee>(`${this.url}/mot-de-passe-oublie`, { email });
  }

  verifierLien(jeton: string): Observable<void> {
    return this.http.post<void>(`${this.url}/reinitialisation/verifier`, { jeton });
  }

  reinitialiser(jeton: string, motDePasse: string): Observable<void> {
    return this.http.post<void>(`${this.url}/reinitialisation`, { jeton, motDePasse });
  }
}
