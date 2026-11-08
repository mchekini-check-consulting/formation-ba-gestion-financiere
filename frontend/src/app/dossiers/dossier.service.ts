import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

/** Dossier client du cabinet (US-10). siren : 9 chiffres, sans espaces. */
export interface DossierClient {
  id: number;
  raisonSociale: string;
  siren: string;
  creeLe: string;
}

export interface DemandeDossier {
  raisonSociale: string;
  siren: string;
}

/** US-10 Créer un dossier client : appels au backend (via le gateway). */
@Injectable({ providedIn: 'root' })
export class DossierService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase()}/dossiers`;

  creer(demande: DemandeDossier): Observable<DossierClient> {
    return this.http.post<DossierClient>(this.url, demande);
  }

  consulter(id: number): Observable<DossierClient> {
    return this.http.get<DossierClient>(`${this.url}/${id}`);
  }
}

/** « 812345679 » -> « 812 345 679 ». */
export function formaterSiren(siren: string): string {
  return siren.replace(/^(\d{3})(\d{3})(\d{3})$/, '$1 $2 $3');
}
