import { Component, OnInit, inject, signal } from '@angular/core';
import { LIBELLES_STATUT_MODULE, Module, ModuleService } from './module.service';

/** US-18 RG2 : message affiché au clic sur un module. */
export const MESSAGE_PROCHAINEMENT = 'Ce module sera disponible prochainement';

/** US-18 Modules à venir, dans le menu de gauche (sous Dossiers / Équipe). */
@Component({
  selector: 'app-modules-menu',
  templateUrl: './modules-menu.html',
  styleUrl: './modules-menu.scss',
})
export class ModulesMenu implements OnInit {
  private readonly modules = inject(ModuleService);
  protected readonly liste = signal<Module[]>([]);
  /** RG2 : module cliqué, dont le message est affiché. */
  protected readonly choisi = signal<Module | null>(null);
  protected readonly messageProchainement = MESSAGE_PROCHAINEMENT;

  ngOnInit(): void {
    // Liste indisponible : le menu reste utilisable, sans la rubrique Modules.
    this.modules.lister().subscribe({
      next: (liste) => this.liste.set(liste),
      error: () => this.liste.set([]),
    });
  }

  ouvrir(module: Module): void {
    this.choisi.set(this.choisi()?.code === module.code ? null : module);
  }

  /** RG3 : un statut inconnu de l'écran s'affiche tel quel. */
  protected libelle(module: Module): string {
    return LIBELLES_STATUT_MODULE[module.statut] ?? module.statut;
  }
}
