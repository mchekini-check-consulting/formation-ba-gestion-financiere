import { Component, OnChanges, inject, input, signal } from '@angular/core';
import { FactureService, LigneFacture, ListeFactures, Sens, formaterDepot } from './facture.service';

const MESSAGE_INDISPONIBLE = 'Le service est momentanément indisponible. Réessayez.';

/** US-12 CA vide, selon l'onglet. */
export const MESSAGES_VIDE: Record<Sens, string> = {
  ACHAT: 'Aucune facture fournisseur pour le moment',
  VENTE: 'Aucune facture client pour le moment',
};

/** US-12 Factures d'un onglet du dossier (US-11 RG2). */
@Component({
  selector: 'app-factures-dossier',
  templateUrl: './factures-dossier.html',
  styleUrl: './factures-dossier.scss',
})
export class FacturesDossier implements OnChanges {
  private readonly factures = inject(FactureService);
  readonly dossierId = input.required<number>();
  readonly sens = input.required<Sens>();
  protected readonly liste = signal<ListeFactures | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly ajoutEnCours = signal(false);

  ngOnChanges(): void {
    this.charger();
  }

  private charger(): void {
    this.liste.set(null);
    this.erreur.set(null);
    this.factures.lister(this.dossierId(), this.sens()).subscribe({
      next: (liste) => this.liste.set(liste),
      error: () => this.erreur.set(MESSAGE_INDISPONIBLE),
    });
  }

  ajouterExemples(): void {
    this.ajoutEnCours.set(true);
    this.factures.ajouterExemples(this.dossierId()).subscribe({
      next: () => {
        this.ajoutEnCours.set(false);
        this.charger();
      },
      error: () => {
        this.ajoutEnCours.set(false);
        this.erreur.set(MESSAGE_INDISPONIBLE);
      },
    });
  }

  protected vide(): string {
    return MESSAGES_VIDE[this.sens()];
  }

  protected depot(facture: LigneFacture): string {
    return formaterDepot(facture.deposeeLe);
  }
}
