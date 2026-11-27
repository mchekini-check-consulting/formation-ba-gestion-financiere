import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnChanges, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { montantEcriture } from './ecritures-facture';
import { CompteComptable, CompteParDefaut, PlanComptableService } from './plan-comptable.service';
import {
  FiltreSens,
  TAUX_TVA,
  Transaction,
  TransactionService,
  categorieParDefaut,
  duMois,
  filtrer,
  formaterDate,
  formaterMontant,
  moisCourant,
  tauxParDefaut,
  totaux,
} from './transaction.service';

const MESSAGE_INDISPONIBLE = 'Le service est momentanément indisponible. Réessayez.';

/** US-30 : saisie du formulaire « Valider » d'une opération. */
interface SaisieValidation {
  categorie: string;
  numeroCompte: string;
  tauxTva: number;
}

/** US-41 RG2 : catégories proposées si le plan comptable ne répond pas. */
const CATEGORIES_PAR_DEFAUT: CompteParDefaut[] = [
  { code: 'ACHAT', libelle: 'Achat', numero: '607' },
  { code: 'VENTE', libelle: 'Vente', numero: '707' },
  { code: 'FRAIS_BANCAIRES', libelle: 'Frais bancaires', numero: '627' },
];

/** US-45 CA3 : aucune transaction (période vide, ou filtres sans résultat). */
export const MESSAGE_VIDE = 'Aucune transaction sur cette période';

/** US-45 RG2 : choix du filtre sens. */
export const FILTRES_SENS: { id: FiltreSens; libelle: string }[] = [
  { id: 'TOUTES', libelle: 'Toutes' },
  { id: 'ENTREE', libelle: 'Entrées' },
  { id: 'SORTIE', libelle: 'Sorties' },
];

/**
 * US-45 Onglet Banque du dossier : entrées et sorties d'argent, filtres et totaux de la période.
 * US-30 : « Valider » une opération (catégorie, compte, taux de TVA) génère son écriture au journal de
 * banque (compte de la catégorie, TVA, banque 512), affichée sous la ligne ; une opération validée
 * ne se valide plus.
 */
@Component({
  selector: 'app-transactions-dossier',
  imports: [FormsModule],
  templateUrl: './transactions-dossier.html',
  styleUrl: './transactions-dossier.scss',
})
export class TransactionsDossier implements OnChanges {
  private readonly service = inject(TransactionService);
  private readonly plans = inject(PlanComptableService);
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

  /** US-30 : opération dont le formulaire « Valider » est ouvert, et celle dont l'écriture est affichée. */
  protected readonly enValidation = signal<string | null>(null);
  protected readonly ecritureOuverte = signal<string | null>(null);
  protected readonly envoiEnCours = signal(false);
  protected readonly erreurValidation = signal<string | null>(null);
  protected readonly categories = signal<CompteParDefaut[]>(CATEGORIES_PAR_DEFAUT);
  protected readonly comptes = signal<CompteComptable[]>([]);
  protected readonly tauxTva = TAUX_TVA;
  protected saisie: SaisieValidation = { categorie: 'ACHAT', numeroCompte: '607', tauxTva: 20 };

  ngOnChanges(): void {
    this.transactions.set(null);
    this.erreur.set(null);
    this.enValidation.set(null);
    this.ecritureOuverte.set(null);
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

  /** US-30 : ouvre le formulaire, pré-rempli selon le sens ; le plan comptable est chargé une fois. */
  ouvrirValidation(transaction: Transaction): void {
    this.ecritureOuverte.set(null);
    this.erreurValidation.set(null);
    this.enValidation.set(transaction.reference);
    this.saisie = { categorie: '', numeroCompte: '', tauxTva: 20 };
    this.choisirCategorie(categorieParDefaut(transaction.sens));
    if (this.comptes().length === 0) {
      this.plans.consulter(this.dossierId()).subscribe({
        next: (plan) => {
          const categories = plan.parDefaut.filter((d) => d.code !== 'BANQUE');
          if (categories.length > 0) {
            this.categories.set(categories);
          }
          this.comptes.set(plan.comptes.filter((c) => c.actif));
          this.choisirCategorie(this.saisie.categorie);
        },
        // Plan indisponible : on garde les catégories par défaut, le backend contrôlera le compte.
        error: () => this.comptes.set([]),
      });
    }
  }

  /** Catégorie choisie : son compte par défaut (US-41 RG2) et le taux habituel. */
  choisirCategorie(code: string): void {
    const categorie = this.categories().find((c) => c.code === code);
    this.saisie = {
      categorie: code,
      numeroCompte: categorie?.numero ?? '',
      tauxTva: tauxParDefaut(code),
    };
  }

  annulerValidation(): void {
    this.enValidation.set(null);
    this.erreurValidation.set(null);
  }

  /** RG1 : envoi de la validation ; la ligne est remplacée par la réponse (« Validée », écriture). */
  valider(transaction: Transaction): void {
    this.envoiEnCours.set(true);
    this.erreurValidation.set(null);
    const demande = {
      categorie: this.saisie.categorie,
      numeroCompte: this.saisie.numeroCompte || null,
      tauxTva: Number(this.saisie.tauxTva),
    };
    this.service.valider(this.dossierId(), transaction.reference, demande).subscribe({
      next: (validee) => {
        this.envoiEnCours.set(false);
        this.enValidation.set(null);
        this.transactions.update((liste) =>
          (liste ?? []).map((t) => (t.reference === validee.reference ? validee : t)),
        );
        this.ecritureOuverte.set(validee.reference);
      },
      error: (err: HttpErrorResponse) => {
        this.envoiEnCours.set(false);
        this.erreurValidation.set(
          [400, 404, 409].includes(err.status) && err.error?.message ? err.error.message : MESSAGE_INDISPONIBLE,
        );
      },
    });
  }

  basculerEcriture(transaction: Transaction): void {
    this.ecritureOuverte.update((r) => (r === transaction.reference ? null : transaction.reference));
  }

  /** Comptes proposés : ceux du plan, plus le compte saisi s'il n'y est pas (le backend le refusera). */
  protected comptesProposes(): CompteComptable[] {
    const numero = this.saisie.numeroCompte;
    const comptes = this.comptes();
    if (numero === '' || comptes.some((c) => c.numero === numero)) {
      return comptes;
    }
    return [{ numero, libelle: '', sousCompte: false, actif: true }, ...comptes];
  }

  protected libelleCategorie(code: string): string {
    return this.categories().find((c) => c.code === code)?.libelle ?? code;
  }

  protected taux(valeur: number): string {
    return `${String(valeur).replace('.', ',')} %`;
  }

  protected montantLigne(valeur: number): string {
    return montantEcriture(valeur);
  }

  protected montant(valeur: number): string {
    return formaterMontant(valeur);
  }

  protected date(transaction: Transaction): string {
    return formaterDate(transaction.date);
  }
}
