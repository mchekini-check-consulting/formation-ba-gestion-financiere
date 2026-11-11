import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';
import { Sens } from '../dossiers/facture.service';

/** US-16 RG1 : une facture déposée par le dirigeant ; supprimable tant que le cabinet ne l'a pas ouverte (RG2, RG3). */
export interface FactureDeposee {
  id: number;
  nomFichier: string;
  deposeeLe: string;
  sens: Sens;
  supprimable: boolean;
}

/** US-15 RG1 : PDF, JPG ou PNG, 10 Mo maximum, 20 fichiers par dépôt. */
export const TAILLE_MAX = 10 * 1024 * 1024;
export const FICHIERS_MAX = 20;
export const MESSAGE_FORMAT = 'Format non accepté : PDF, JPG ou PNG uniquement';
export const MESSAGE_TAILLE = 'Fichier trop volumineux : 10 Mo maximum';
const TYPES = ['application/pdf', 'image/jpeg', 'image/png'];
const EXTENSION = /\.(pdf|jpe?g|png)$/i;

/**
 * Vérification avant l'envoi, pour répondre tout de suite : null si le fichier peut partir,
 * sinon le motif du refus. Le serveur revérifie le vrai type et la taille.
 */
export function refusAvantEnvoi(fichier: File): string | null {
  if (!TYPES.includes(fichier.type) && !EXTENSION.test(fichier.name)) {
    return MESSAGE_FORMAT;
  }
  if (fichier.size > TAILLE_MAX) {
    return MESSAGE_TAILLE;
  }
  return null;
}

/** Espace client du dirigeant (US-15, US-38, US-16) : appels au backend (via le gateway). */
@Injectable({ providedIn: 'root' })
export class EspaceClientService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase()}/espace-client/factures`;

  lister(sens: Sens): Observable<FactureDeposee[]> {
    return this.http.get<FactureDeposee[]>(this.url, { params: new HttpParams().set('sens', sens) });
  }

  /** Un fichier par appel (US-15) : un fichier refusé n'empêche pas les autres (RG2). */
  deposer(sens: Sens, fichier: File): Observable<FactureDeposee> {
    const corps = new FormData();
    corps.append('fichier', fichier, fichier.name);
    return this.http.post<FactureDeposee>(this.url, corps, { params: new HttpParams().set('sens', sens) });
  }

  supprimer(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
