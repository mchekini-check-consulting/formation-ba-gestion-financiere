import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

/** Dossier client du cabinet (US-10). siren : 9 chiffres, sans espaces. */
export interface DossierClient {
  id: number;
  raisonSociale: string;
  siren: string;
  creeLe: string;
  /** US-11 RG1 : état de l'espace client, affiché dans l'en-tête du dossier. */
  etatEspaceClient: EtatEspaceClient;
}

export interface DemandeDossier {
  raisonSociale: string;
  siren: string;
}

/** US-09 RG1 / US-14 RG3 : état de l'espace client du dossier. */
export type EtatEspaceClient = 'NON_INVITE' | 'INVITE' | 'ACTIF';

export const LIBELLES_ETAT: Record<EtatEspaceClient, string> = {
  NON_INVITE: 'Non invité',
  INVITE: 'Invité',
  ACTIF: 'Actif',
};

/** US-09 RG1 : une ligne de la liste des dossiers du cabinet. */
export interface LigneDossier {
  id: number;
  raisonSociale: string;
  siren: string;
  nombreFactures: number;
  etatEspaceClient: EtatEspaceClient;
  /** US-20 : comptables qui suivent le dossier. */
  comptables: string[];
}

/** US-10 Créer un dossier client : appels au backend (via le gateway). */
@Injectable({ providedIn: 'root' })
export class DossierService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase()}/dossiers`;

  /** US-09 : dossiers du cabinet ; RG2 : filtrés par raison sociale ou SIREN si recherche est rempli. */
  lister(recherche = ''): Observable<LigneDossier[]> {
    const texte = recherche.trim();
    const params = texte ? new HttpParams().set('recherche', texte) : undefined;
    return this.http.get<LigneDossier[]>(this.url, { params });
  }

  creer(demande: DemandeDossier): Observable<DossierClient> {
    return this.http.post<DossierClient>(this.url, demande);
  }

  /** US-11 : id tel que lu dans l'adresse ; le serveur refuse ce qui n'est pas un dossier du cabinet. */
  consulter(id: number | string): Observable<DossierClient> {
    return this.http.get<DossierClient>(`${this.url}/${id}`);
  }
}

/** « 812345679 » -> « 812 345 679 ». */
export function formaterSiren(siren: string): string {
  return siren.replace(/^(\d{3})(\d{3})(\d{3})$/, '$1 $2 $3');
}
