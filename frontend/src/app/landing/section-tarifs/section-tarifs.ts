import { Component, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { INCLUS } from '../contenu';
import { simuler, TRANCHES } from '../tarifs';

const EUROS = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 });

// Section « Tarifs » : grille par tranches et simulateur du prix mensuel
@Component({
  selector: 'app-section-tarifs',
  imports: [RouterLink],
  styleUrl: './section-tarifs.scss',
  templateUrl: './section-tarifs.html',
})
export class SectionTarifs {
  protected readonly tranches = TRANCHES;
  protected readonly inclus = INCLUS;

  /** Nombre de dossiers saisi dans le simulateur (300 = exemple de la maquette) */
  protected readonly nombreDossiers = signal(300);
  protected readonly simulation = computed(() => simuler(this.nombreDossiers()));

  protected saisirDossiers(valeur: string): void {
    this.nombreDossiers.set(Number(valeur));
  }

  protected euros(montant: number): string {
    return `${EUROS.format(montant)} €`;
  }
}
