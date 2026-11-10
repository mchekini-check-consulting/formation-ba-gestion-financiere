import { Component, OnChanges, inject, input, signal } from '@angular/core';
import { LIBELLES_STATUT_MODULE, ModuleDossier, ModuleService } from './module.service';

const MESSAGE_INDISPONIBLE = 'Le service est momentanément indisponible. Réessayez.';
/** US-18 RG2 : message affiché au clic sur une tuile. */
export const MESSAGE_PROCHAINEMENT = 'Ce module sera disponible prochainement';

/** US-18 Onglet Modules du dossier (US-11 RG2) : tuiles des modules à venir. */
@Component({
  selector: 'app-modules-dossier',
  templateUrl: './modules-dossier.html',
  styleUrl: './modules-dossier.scss',
})
export class ModulesDossier implements OnChanges {
  private readonly modules = inject(ModuleService);
  readonly dossierId = input.required<number>();
  protected readonly liste = signal<ModuleDossier[] | null>(null);
  protected readonly erreur = signal<string | null>(null);
  /** RG2 : module cliqué, dont le message est affiché. */
  protected readonly choisi = signal<ModuleDossier | null>(null);
  protected readonly messageProchainement = MESSAGE_PROCHAINEMENT;

  ngOnChanges(): void {
    this.liste.set(null);
    this.erreur.set(null);
    this.choisi.set(null);
    this.modules.lister(this.dossierId()).subscribe({
      next: (liste) => this.liste.set(liste),
      error: () => this.erreur.set(MESSAGE_INDISPONIBLE),
    });
  }

  ouvrir(module: ModuleDossier): void {
    this.choisi.set(module);
  }

  /** RG3 : un statut inconnu de l'écran s'affiche tel quel. */
  protected libelle(module: ModuleDossier): string {
    return LIBELLES_STATUT_MODULE[module.statut] ?? module.statut;
  }
}
