import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

/** US-12 RG1 : factures fournisseurs (achats) ou factures clients (ventes). */
export type Sens = 'ACHAT' | 'VENTE';

/** US-12 RG2 : une ligne de la liste ; RG3 : nouvelle tant que personne du cabinet ne l'a ouverte. */
export interface LigneFacture {
  id: number;
  nomFichier: string;
  deposeeLe: string;
  deposeePar: string;
  statut: 'RECUE';
  nouvelle: boolean;
}

/** US-13 : en-tête de la page d'aperçu. */
export interface VueFacture {
  id: number;
  nomFichier: string;
  typeFichier: string;
  sens: Sens;
}

/** exemplesPossibles : mode test seulement (dev, qualif), jamais en preprod ni en prod. */
export interface ListeFactures {
  factures: LigneFacture[];
  exemplesPossibles: boolean;
}

/** US-12 Factures d'un dossier : appels au backend (via le gateway). */
@Injectable({ providedIn: 'root' })
export class FactureService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase()}/dossiers`;

  lister(dossierId: number, sens: Sens): Observable<ListeFactures> {
    return this.http.get<ListeFactures>(`${this.url}/${dossierId}/factures`, {
      params: new HttpParams().set('sens', sens),
    });
  }

  /** US-13 : nom et type du fichier, pour la page d'aperçu. */
  consulter(dossierId: number | string, factureId: number | string): Observable<VueFacture> {
    return this.http.get<VueFacture>(`${this.url}/${dossierId}/factures/${factureId}`);
  }

  /** US-13 RG1 : fichier pour l'aperçu ; RG2 : fichier d'origine à télécharger. */
  fichier(dossierId: number | string, factureId: number | string, telechargement: boolean): Observable<Blob> {
    const params = telechargement ? new HttpParams().set('telechargement', 'true') : undefined;
    return this.http.get(`${this.url}/${dossierId}/factures/${factureId}/fichier`, { params, responseType: 'blob' });
  }

  /** Mode test : factures d'exemple, le dépôt (US-15) n'existant pas encore. */
  ajouterExemples(dossierId: number): Observable<void> {
    return this.http.post<void>(`${this.url}/${dossierId}/factures/exemples`, null);
  }
}

/** « 2026-11-08T09:05:00Z » -> « 08/11/2026 à 10:05 » (heure de Paris). */
export function formaterDepot(date: string): string {
  const d = new Date(date);
  const jour = d.toLocaleDateString('fr-FR', { timeZone: 'Europe/Paris' });
  const heure = d.toLocaleTimeString('fr-FR', { timeZone: 'Europe/Paris', hour: '2-digit', minute: '2-digit' });
  return `${jour} à ${heure}`;
}
