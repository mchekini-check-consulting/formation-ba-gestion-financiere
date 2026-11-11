import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  Subject,
  Subscription,
  catchError,
  debounceTime,
  distinctUntilChanged,
  of,
  startWith,
  switchMap,
} from 'rxjs';
import { SessionService } from '../session/session.service';
import { nomsComptables } from './comptables-dossier';
import { DossierService, EtatEspaceClient, LIBELLES_ETAT, LigneDossier, formaterSiren } from './dossier.service';

const MESSAGE_INDISPONIBLE = 'Le service est momentanément indisponible. Réessayez.';

/** Attente après la dernière touche avant d'interroger le serveur (RG2). */
export const DELAI_RECHERCHE_MS = 300;

/**
 * US-09 Consulter la liste des dossiers du cabinet : raison sociale, SIREN, nombre de factures et
 * état de l'espace client (RG1), recherche par raison sociale ou SIREN (RG2). RG3 : le backend ne
 * renvoie que les dossiers du cabinet de la personne connectée.
 */
@Component({
  imports: [RouterLink],
  selector: 'app-liste-dossiers',
  styleUrl: './liste-dossiers.scss',
  templateUrl: './liste-dossiers.html',
})
export class ListeDossiers implements OnInit, OnDestroy {
  private readonly dossiers = inject(DossierService);
  private readonly session = inject(SessionService);
  private readonly recherches = new Subject<string>();
  private abonnement?: Subscription;

  protected readonly nom = computed(() => this.session.utilisateur()?.nomAffiche ?? '');
  protected readonly lignes = signal<LigneDossier[] | null>(null);
  protected readonly recherche = signal('');
  protected readonly erreur = signal<string | null>(null);

  ngOnInit(): void {
    this.abonnement = this.recherches
      .pipe(
        debounceTime(DELAI_RECHERCHE_MS),
        startWith(''),
        distinctUntilChanged(),
        // Une panne n'arrête pas la recherche : la saisie suivante réessaie.
        // Refus du serveur (ex. compte sans cabinet) : son message ; sinon « indisponible ».
        switchMap((texte) =>
          this.dossiers.lister(texte).pipe(
            catchError((err: HttpErrorResponse) =>
              of(err.status === 403 && err.error?.message ? String(err.error.message) : MESSAGE_INDISPONIBLE),
            ),
          ),
        ),
      )
      .subscribe((reponse) => {
        if (typeof reponse === 'string') {
          this.erreur.set(reponse);
        } else {
          this.erreur.set(null);
          this.lignes.set(reponse);
        }
      });
  }

  ngOnDestroy(): void {
    this.abonnement?.unsubscribe();
  }

  chercher(texte: string): void {
    this.recherche.set(texte);
    this.recherches.next(texte.trim());
  }

  protected siren(ligne: LigneDossier): string {
    return formaterSiren(ligne.siren);
  }

  protected libelleEtat(etat: EtatEspaceClient): string {
    return LIBELLES_ETAT[etat];
  }

  /** US-20 : colonne « Comptables ». */
  protected comptables(ligne: LigneDossier): string {
    return nomsComptables(ligne.comptables ?? []);
  }

  protected factures(nombre: number): string {
    return nombre > 1 ? `${nombre} factures` : `${nombre} facture`;
  }
}
