import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, output, signal } from '@angular/core';
import { FactureService, VueEcriture, VueFacture, dateOuTiret, formaterDepot } from './facture.service';

const MESSAGE_INDISPONIBLE = 'Le service est momentanément indisponible. Réessayez.';
const EUROS = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' });

/** Montant d'une colonne Débit ou Crédit : vide quand il est nul. */
export function montantEcriture(valeur: number): string {
  return valeur === 0 ? '' : EUROS.format(valeur);
}

/**
 * US-56 Écritures d'une facture fournisseur validée (bloc « Écritures générées » de la fiche).
 * RG1 à RG4 : lignes au journal des achats, totaux débit / crédit (RG2). RG5 : « Re-générer les
 * écritures » recalcule l'écriture après une correction (« À re-générer »), tant qu'elle n'est pas
 * exportée ; CA3 : exportée, le bouton est désactivé avec la mention « Exportée ».
 * US-57 : même bloc pour une facture client, au journal des ventes (VT).
 */
@Component({
  selector: 'app-ecritures-facture',
  templateUrl: './ecritures-facture.html',
  styleUrl: './ecritures-facture.scss',
})
export class EcrituresFacture {
  private readonly factures = inject(FactureService);
  readonly dossierId = input.required<string>();
  readonly facture = input.required<VueFacture>();
  /** Écriture re-générée : la page affiche la fiche à jour. */
  readonly modifiee = output<VueFacture>();

  protected readonly envoiEnCours = signal(false);
  protected readonly erreur = signal<string | null>(null);

  regenerer(): void {
    this.envoiEnCours.set(true);
    this.erreur.set(null);
    this.factures.regenererEcritures(this.dossierId(), this.facture().id).subscribe({
      next: (vue) => {
        this.envoiEnCours.set(false);
        this.modifiee.emit(vue);
      },
      error: (err: HttpErrorResponse) => {
        this.envoiEnCours.set(false);
        this.erreur.set([400, 409].includes(err.status) && err.error?.message ? err.error.message : MESSAGE_INDISPONIBLE);
      },
    });
  }

  protected montant(valeur: number): string {
    return montantEcriture(valeur);
  }

  protected total(valeur: number): string {
    return EUROS.format(valeur);
  }

  protected nomJournal(journal: VueEcriture['journal']): string {
    return journal === 'VT' ? 'Journal des ventes' : 'Journal des achats';
  }

  protected date(jour: string): string {
    return dateOuTiret(jour);
  }

  protected generee(instant: string): string {
    return formaterDepot(instant);
  }
}
