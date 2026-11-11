import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { catchError, concatMap, from, map, of, toArray } from 'rxjs';
import { Sens, formaterDepot } from '../dossiers/facture.service';
import { SessionService } from '../session/session.service';
import { EspaceClientService, FICHIERS_MAX, FactureDeposee, refusAvantEnvoi } from './espace-client.service';

const MESSAGE_INDISPONIBLE = 'Le service est momentanément indisponible. Réessayez.';
export const MESSAGE_TROP_DE_FICHIERS = `Vous pouvez déposer ${FICHIERS_MAX} fichiers maximum à la fois`;
export const MESSAGE_SUPPRIMEE = 'Facture supprimée';

/** US-38 RG1 : le dirigeant choisit l'espace avant de déposer. */
export const ONGLETS_ESPACE: { sens: Sens; libelle: string }[] = [
  { sens: 'ACHAT', libelle: 'Factures fournisseurs (achats)' },
  { sens: 'VENTE', libelle: 'Factures clients (ventes)' },
];

export const MESSAGES_VIDE_ESPACE: Record<Sens, string> = {
  ACHAT: 'Aucune facture fournisseur déposée pour le moment',
  VENTE: 'Aucune facture client déposée pour le moment',
};

/** Un fichier refusé et son motif (US-15 RG2 : il n'empêche pas le dépôt des autres). */
export interface FichierRefuse {
  nom: string;
  motif: string;
}

interface ResultatDepot {
  message: string;
  refus: FichierRefuse[];
}

function factures(nombre: number): string {
  return nombre > 1 ? `${nombre} factures déposées` : `${nombre} facture déposée`;
}

/** US-15 CA1 « 1 facture déposée », CA2 motif du refus, CA3 « 2 factures déposées, 1 refusée ». */
export function resumeDepot(deposees: number, refus: FichierRefuse[]): string {
  if (refus.length === 0) {
    return factures(deposees);
  }
  if (deposees === 0 && refus.length === 1) {
    return refus[0].motif;
  }
  const refusees = refus.length > 1 ? `${refus.length} refusées` : '1 refusée';
  return `${deposees === 0 ? 'Aucune facture déposée' : factures(deposees)}, ${refusees}`;
}

/**
 * Espace client du dirigeant (page d'arrivée après la connexion, US-03 RG3) : US-15 déposer
 * ses factures fournisseurs, US-38 ses factures clients, US-16 suivre et supprimer ses dépôts.
 */
@Component({
  selector: 'app-espace-client',
  templateUrl: './espace-client.html',
  styleUrl: './espace-client.scss',
})
export class EspaceClient implements OnInit {
  private readonly session = inject(SessionService);
  private readonly espace = inject(EspaceClientService);

  protected readonly nom = computed(() => this.session.utilisateur()?.nomAffiche ?? '');
  protected readonly onglets = ONGLETS_ESPACE;
  protected readonly sens = signal<Sens>('ACHAT');
  protected readonly factures = signal<FactureDeposee[] | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly envoiEnCours = signal(false);
  protected readonly survol = signal(false);
  protected readonly resultat = signal<ResultatDepot | null>(null);
  protected readonly aConfirmer = signal<number | null>(null);
  protected readonly messageSuppression = signal<string | null>(null);

  ngOnInit(): void {
    this.charger();
  }

  choisir(sens: Sens): void {
    this.sens.set(sens);
    this.resultat.set(null);
    this.messageSuppression.set(null);
    this.aConfirmer.set(null);
    this.charger();
  }

  private charger(): void {
    const sens = this.sens();
    this.factures.set(null);
    this.erreur.set(null);
    this.espace.lister(sens).subscribe({
      next: (liste) => {
        if (this.sens() === sens) {
          this.factures.set(liste);
        }
      },
      error: () => this.erreur.set(MESSAGE_INDISPONIBLE),
    });
  }

  /** Bouton « Choisir des fichiers ». */
  choisirFichiers(champ: HTMLInputElement): void {
    this.deposer(Array.from(champ.files ?? []));
    champ.value = '';
  }

  survoler(evenement: DragEvent, dessus: boolean): void {
    evenement.preventDefault();
    this.survol.set(dessus);
  }

  /** Glisser-déposer (RG1). */
  lacher(evenement: DragEvent): void {
    evenement.preventDefault();
    this.survol.set(false);
    this.deposer(Array.from(evenement.dataTransfer?.files ?? []));
  }

  /** RG1 : 20 fichiers par dépôt ; RG2 : chaque fichier part seul, un refus n'empêche pas les autres. */
  deposer(fichiers: File[]): void {
    if (fichiers.length === 0 || this.envoiEnCours()) {
      return;
    }
    this.messageSuppression.set(null);
    if (fichiers.length > FICHIERS_MAX) {
      this.resultat.set({ message: MESSAGE_TROP_DE_FICHIERS, refus: [] });
      return;
    }
    const sens = this.sens();
    this.envoiEnCours.set(true);
    this.resultat.set(null);
    from(fichiers)
      .pipe(
        concatMap((fichier) => {
          const motif = refusAvantEnvoi(fichier);
          if (motif) {
            return of<FichierRefuse | null>({ nom: fichier.name, motif });
          }
          return this.espace.deposer(sens, fichier).pipe(
            map(() => null),
            catchError((err: HttpErrorResponse) =>
              of<FichierRefuse | null>({
                nom: fichier.name,
                motif: err.status === 400 && err.error?.message ? String(err.error.message) : MESSAGE_INDISPONIBLE,
              }),
            ),
          );
        }),
        toArray(),
      )
      .subscribe((issues) => {
        const refus = issues.filter((issue): issue is FichierRefuse => issue !== null);
        const deposees = fichiers.length - refus.length;
        this.resultat.set({ message: resumeDepot(deposees, refus), refus });
        this.envoiEnCours.set(false);
        if (deposees > 0 && this.sens() === sens) {
          this.charger();
        }
      });
  }

  /** US-16 CA1 : « Supprimer », puis confirmer. */
  demanderSuppression(facture: FactureDeposee): void {
    this.aConfirmer.set(facture.id);
    this.messageSuppression.set(null);
  }

  annulerSuppression(): void {
    this.aConfirmer.set(null);
  }

  confirmerSuppression(facture: FactureDeposee): void {
    this.aConfirmer.set(null);
    this.espace.supprimer(facture.id).subscribe({
      next: () => {
        this.factures.update((liste) => liste?.filter((f) => f.id !== facture.id) ?? liste);
        this.messageSuppression.set(MESSAGE_SUPPRIMEE);
      },
      error: (err: HttpErrorResponse) => {
        // US-16 RG3 : le cabinet l'a ouverte entre-temps ; la liste est rafraîchie.
        this.messageSuppression.set(
          [404, 409].includes(err.status) && err.error?.message ? String(err.error.message) : MESSAGE_INDISPONIBLE,
        );
        if ([404, 409].includes(err.status)) {
          this.charger();
        }
      },
    });
  }

  protected vide(): string {
    return MESSAGES_VIDE_ESPACE[this.sens()];
  }

  protected depot(facture: FactureDeposee): string {
    return formaterDepot(facture.deposeeLe);
  }
}
