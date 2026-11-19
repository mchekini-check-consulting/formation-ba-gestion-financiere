import { Component, OnChanges, computed, inject, input, signal } from '@angular/core';
import {
  FiltreSens,
  Transaction,
  TransactionService,
  duMois,
  filtrer,
  formaterDate,
  formaterMontant,
  moisCourant,
  totaux,
} from './transaction.service';

const MESSAGE_INDISPONIBLE = 'Le service est momentanément indisponible. Réessayez.';

/** US-45 CA3 : aucune transaction (période vide, ou filtres sans résultat). */
export const MESSAGE_VIDE = 'Aucune transaction sur cette période';

/** US-45 RG2 : choix du filtre sens. */
export const FILTRES_SENS: { id: FiltreSens; libelle: string }[] = [
  { id: 'TOUTES', libelle: 'Toutes' },
  { id: 'ENTREE', libelle: 'Entrées' },
  { id: 'SORTIE', libelle: 'Sorties' },
];

/** US-45 Onglet Banque du dossier : entrées et sorties d'argent, filtres et totaux de la période. */
@Component({
  selector: 'app-transactions-dossier',
  templateUrl: './transactions-dossier.html',
  styleUrl: './transactions-dossier.scss',
})
export class TransactionsDossier implements OnChanges {
  private readonly service = inject(TransactionService);
  readonly dossierId = input.required<number>();
  protected readonly transactions = signal<Transaction[] | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly mois = signal(moisCourant());
  protected readonly sens = signal<FiltreSens>('TOUTES');
  protected readonly recherche = signal('');
  protected readonly filtresSens = FILTRES_SENS;
  protected readonly vide = MESSAGE_VIDE;

  /** RG2 : transactions du mois choisi. */
  private readonly periode = computed(() => duMois(this.transactions() ?? [], this.mois()));
  /** RG3 : totaux de toute la période (le filtre sens et la recherche ne changent que la liste). */
  protected readonly totauxPeriode = computed(() => totaux(this.periode()));
  /** RG1, RG2, RG4 : lignes affichées, déjà triées par le backend (les plus récentes d'abord). */
  protected readonly lignes = computed(() => filtrer(this.periode(), this.sens(), this.recherche()));
  /** US-58 : données de démonstration toujours signalées. */
  protected readonly demonstration = computed(() => (this.transactions() ?? []).some((t) => t.source === 'BOUCHON'));

  ngOnChanges(): void {
    this.transactions.set(null);
    this.erreur.set(null);
    this.service.lister(this.dossierId()).subscribe({
      next: (transactions) => this.transactions.set(transactions),
      error: () => this.erreur.set(MESSAGE_INDISPONIBLE),
    });
  }

  choisirMois(mois: string): void {
    // Champ effacé : retour au mois en cours plutôt qu'une période vide.
    this.mois.set(/^\d{4}-\d{2}$/.test(mois) ? mois : moisCourant());
  }

  choisirSens(sens: FiltreSens): void {
    this.sens.set(sens);
  }

  chercher(texte: string): void {
    this.recherche.set(texte);
  }

  protected montant(valeur: number): string {
    return formaterMontant(valeur);
  }

  protected date(transaction: Transaction): string {
    return formaterDate(transaction.date);
  }
}
