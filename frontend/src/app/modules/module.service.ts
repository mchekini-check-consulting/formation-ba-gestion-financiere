import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

/** US-18 RG1 : seul statut pour l'instant. */
export type StatutModule = 'BIENTOT_DISPONIBLE';

/** US-18 : libellé court du badge, dans le menu de gauche. */
export const LIBELLES_STATUT_MODULE: Record<StatutModule, string> = {
  BIENTOT_DISPONIBLE: 'Bientôt',
};

/** US-18 : un module du menu de gauche ; RG3 : la liste est réglée par le serveur. */
export interface Module {
  code: string;
  nom: string;
  description: string | null;
  statut: StatutModule;
}

/** US-18 Modules à venir : appel au backend (via le gateway). */
@Injectable({ providedIn: 'root' })
export class ModuleService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase()}/modules`;

  lister(): Observable<Module[]> {
    return this.http.get<Module[]>(this.url);
  }
}
