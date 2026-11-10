import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

/** US-18 RG1 : seul statut pour l'instant. */
export type StatutModule = 'BIENTOT_DISPONIBLE';

export const LIBELLES_STATUT_MODULE: Record<StatutModule, string> = {
  BIENTOT_DISPONIBLE: 'Bientôt disponible',
};

/** US-18 : une tuile de l'onglet Modules ; RG3 : la liste est réglée par le serveur. */
export interface ModuleDossier {
  code: string;
  nom: string;
  description: string | null;
  statut: StatutModule;
}

/** US-18 Modules à venir d'un dossier : appel au backend (via le gateway). */
@Injectable({ providedIn: 'root' })
export class ModuleService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase()}/dossiers`;

  lister(dossierId: number): Observable<ModuleDossier[]> {
    return this.http.get<ModuleDossier[]>(`${this.url}/${dossierId}/modules`);
  }
}
