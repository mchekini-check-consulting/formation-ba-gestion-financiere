import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnChanges, computed, inject, input, signal } from '@angular/core';
import {
  CompteComptable,
  ErreursFormulaire,
  NouveauCompte,
  PlanComptable,
  PlanComptableService,
  filtrerComptes,
  trierComptes,
} from './plan-comptable.service';

const MESSAGE_INDISPONIBLE = 'Le service est momentanément indisponible. Réessayez.';

/** US-41 Aucun compte ne correspond à la recherche. */
export const MESSAGE_AUCUN_COMPTE = 'Aucun compte ne correspond à la recherche';

/**
 * US-41 Onglet « Plan comptable » du dossier. RG1 : plan général du dossier et sous-comptes
 * ajoutés (ex. 401MOULINS) ; RG2 : comptes par défaut des catégories et de la banque ; RG3 : un
 * compte se désactive, seul un sous-compte se supprime. Les règles sont vérifiées par le backend.
 */
@Component({
  selector: 'app-plan-comptable-dossier',
  templateUrl: './plan-comptable-dossier.html',
  styleUrl: './plan-comptable-dossier.scss',
})
export class PlanComptableDossier implements OnChanges {
  private readonly service = inject(PlanComptableService);
  readonly dossierId = input.required<number>();
  protected readonly plan = signal<PlanComptable | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly recherche = signal('');
  protected readonly numero = signal('');
  protected readonly libelle = signal('');
  protected readonly erreurs = signal<ErreursFormulaire['erreurs']>({});
  protected readonly alerte = signal<string | null>(null);
  protected readonly enCours = signal(false);
  protected readonly confirmation = signal<string | null>(null);
  protected readonly aucunCompte = MESSAGE_AUCUN_COMPTE;

  protected readonly lignes = computed(() => filtrerComptes(this.plan()?.comptes ?? [], this.recherche()));

  ngOnChanges(): void {
    this.plan.set(null);
    this.erreur.set(null);
    this.service.consulter(this.dossierId()).subscribe({
      next: (plan) => this.plan.set(plan),
      error: () => this.erreur.set(MESSAGE_INDISPONIBLE),
    });
  }

  chercher(texte: string): void {
    this.recherche.set(texte);
  }

  saisirNumero(texte: string): void {
    this.numero.set(texte);
  }

  saisirLibelle(texte: string): void {
    this.libelle.set(texte);
  }

  /** CA1 : nouveau compte ajouté au plan ; CA2 : « Numéro de compte invalide » sous le champ. */
  creer(): void {
    const compte: NouveauCompte = { numero: this.numero(), libelle: this.libelle() };
    this.enCours.set(true);
    this.erreurs.set({});
    this.alerte.set(null);
    this.confirmation.set(null);
    this.service.creer(this.dossierId(), compte).subscribe({
      next: (cree) => {
        this.enCours.set(false);
        this.remplacer(cree);
        this.numero.set('');
        this.libelle.set('');
        this.confirmation.set(`Compte ${cree.numero} « ${cree.libelle} » ajouté au plan`);
      },
      error: (err: HttpErrorResponse) => {
        this.enCours.set(false);
        const corps = err.error as Partial<ErreursFormulaire> | null;
        if ([400, 409].includes(err.status) && corps?.erreurs) {
          this.erreurs.set(corps.erreurs);
        } else {
          this.alerte.set(MESSAGE_INDISPONIBLE);
        }
      },
    });
  }

  /** RG3 : désactiver ou réactiver un compte. */
  basculer(compte: CompteComptable): void {
    this.alerte.set(null);
    this.confirmation.set(null);
    this.service.changerActivation(this.dossierId(), compte.numero, !compte.actif).subscribe({
      next: (modifie) => this.remplacer(modifie),
      error: (err: HttpErrorResponse) => this.alerte.set(this.message(err)),
    });
  }

  /** RG3 : seul un sous-compte se supprime, après confirmation. */
  supprimer(compte: CompteComptable): void {
    if (!window.confirm(`Supprimer le compte ${compte.numero} « ${compte.libelle} » ?`)) {
      return;
    }
    this.alerte.set(null);
    this.confirmation.set(null);
    this.service.supprimer(this.dossierId(), compte.numero).subscribe({
      next: () =>
        this.plan.update((plan) =>
          plan ? { ...plan, comptes: plan.comptes.filter((c) => c.numero !== compte.numero) } : plan,
        ),
      error: (err: HttpErrorResponse) => this.alerte.set(this.message(err)),
    });
  }

  private remplacer(compte: CompteComptable): void {
    this.plan.update((plan) =>
      plan
        ? { ...plan, comptes: trierComptes([...plan.comptes.filter((c) => c.numero !== compte.numero), compte]) }
        : plan,
    );
  }

  /** Message du backend (compte du plan général, compte introuvable) ; sinon service indisponible. */
  private message(err: HttpErrorResponse): string {
    const corps = err.error as { message?: string } | null;
    return [404, 409].includes(err.status) && corps?.message ? corps.message : MESSAGE_INDISPONIBLE;
  }
}
