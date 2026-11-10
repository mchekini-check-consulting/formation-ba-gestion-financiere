import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';
import { Role } from '../connexion/auth.service';
import { Formulaire } from './validation';

/** Étape 1 réussie : le code est parti vers l'email saisi. */
export interface CodeEnvoye {
  defi: string;
  emailMasque: string;
}

/** Étape 2 réussie : cabinet créé, son administrateur est connecté (US-07). */
export interface CabinetCree {
  jeton: string;
  role: Role;
  nomAffiche: string;
  nomCabinet: string;
}

/** US-36 Créer le compte de son cabinet : appels au backend (via le gateway). */
@Injectable({ providedIn: 'root' })
export class InscriptionService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase()}/inscription`;

  envoyerFormulaire(formulaire: Formulaire): Observable<CodeEnvoye> {
    return this.http.post<CodeEnvoye>(this.url, formulaire);
  }

  validerCode(defi: string, code: string): Observable<CabinetCree> {
    return this.http.post<CabinetCree>(`${this.url}/code`, { defi, code });
  }

  renvoyerCode(defi: string): Observable<void> {
    return this.http.post<void>(`${this.url}/code/renvoyer`, { defi });
  }
}
