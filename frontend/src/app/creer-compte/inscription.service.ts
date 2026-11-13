import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';
import { Role } from '../connexion/auth.service';
import { Formulaire } from './validation';

/** Formulaire accepté : cabinet créé, son administrateur est connecté (US-07). */
export interface CabinetCree {
  jeton: string;
  role: Role;
  nomAffiche: string;
  nomCabinet: string;
}

/** US-36 Créer le compte de son cabinet : appel au backend (via le gateway), sans code par email. */
@Injectable({ providedIn: 'root' })
export class InscriptionService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase()}/inscription`;

  creerCabinet(formulaire: Formulaire): Observable<CabinetCree> {
    return this.http.post<CabinetCree>(this.url, formulaire);
  }
}
