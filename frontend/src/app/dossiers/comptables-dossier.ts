import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, Injectable, OnChanges, inject, input, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

/** US-20 : un comptable du cabinet, coché s'il suit le dossier. */
export interface ComptableDossier {
  id: number;
  nomAffiche: string;
  attribue: boolean;
}

/** modifiable : RG1, seuls l'administrateur et les associés attribuent les dossiers. */
export interface ComptablesDossier {
  comptables: ComptableDossier[];
  modifiable: boolean;
}

const MESSAGE_INDISPONIBLE = 'Le service est momentanément indisponible. Réessayez.';

/** US-20 Comptables d'un dossier : appels au backend (via le gateway). */
@Injectable({ providedIn: 'root' })
export class AttributionService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase()}/dossiers`;

  lister(dossierId: number): Observable<ComptablesDossier> {
    return this.http.get<ComptablesDossier>(`${this.url}/${dossierId}/comptables`);
  }

  attribuer(dossierId: number, comptableIds: number[]): Observable<ComptablesDossier> {
    return this.http.put<ComptablesDossier>(`${this.url}/${dossierId}/comptables`, { comptableIds });
  }
}

/** « Lucie Bernard, Marc Petit » ; « Aucun comptable » sinon. */
export function nomsComptables(noms: string[]): string {
  return noms.length === 0 ? 'Aucun comptable' : noms.join(', ');
}

/**
 * US-20 Comptables qui suivent le dossier, dans son en-tête. RG1 : l'administrateur et les
 * associés les choisissent avec « Attribuer » ; un comptable ne voit que la liste.
 */
@Component({
  selector: 'app-comptables-dossier',
  templateUrl: './comptables-dossier.html',
  styleUrl: './comptables-dossier.scss',
})
export class ComptablesDossierComponent implements OnChanges {
  private readonly attributions = inject(AttributionService);
  readonly dossierId = input.required<number>();
  protected readonly vue = signal<ComptablesDossier | null>(null);
  protected readonly edition = signal(false);
  protected readonly choisis = signal<Set<number>>(new Set());
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);

  ngOnChanges(): void {
    this.vue.set(null);
    this.edition.set(false);
    this.attributions.lister(this.dossierId()).subscribe({
      next: (vue) => this.vue.set(vue),
      error: () => this.erreur.set(MESSAGE_INDISPONIBLE),
    });
  }

  protected noms(vue: ComptablesDossier): string {
    return nomsComptables(vue.comptables.filter((c) => c.attribue).map((c) => c.nomAffiche));
  }

  ouvrir(vue: ComptablesDossier): void {
    this.choisis.set(new Set(vue.comptables.filter((c) => c.attribue).map((c) => c.id)));
    this.erreur.set(null);
    this.edition.set(true);
  }

  basculer(id: number): void {
    this.choisis.update((avant) => {
      const apres = new Set(avant);
      if (apres.has(id)) {
        apres.delete(id);
      } else {
        apres.add(id);
      }
      return apres;
    });
  }

  annuler(): void {
    this.edition.set(false);
  }

  /** CA1 : Jean attribue Garage Morel SAS à Lucie. */
  enregistrer(): void {
    this.enCours.set(true);
    this.erreur.set(null);
    this.attributions.attribuer(this.dossierId(), [...this.choisis()]).subscribe({
      next: (vue) => {
        this.vue.set(vue);
        this.enCours.set(false);
        this.edition.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.enCours.set(false);
        this.erreur.set([400, 403].includes(err.status) && err.error?.message ? String(err.error.message) : MESSAGE_INDISPONIBLE);
      },
    });
  }
}
