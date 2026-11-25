import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnChanges, OnDestroy, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, concatMap, from, map, of, toArray } from 'rxjs';
import { FICHIERS_MAX, refusAvantEnvoi } from '../espace/espace-client.service';
import {
  FactureService,
  LIBELLES_LECTURE,
  LIBELLES_STATUT,
  LigneFacture,
  ListeFactures,
  MESSAGE_MOTIF_MANQUANT,
  PASTILLES_LECTURE,
  PASTILLES_STATUT,
  SOURCES,
  STATUTS,
  Sens,
  Statut,
  dateOuTiret,
  formaterDepot,
  montantOuTiret,
} from './facture.service';

const MESSAGE_INDISPONIBLE = 'Le service est momentanément indisponible. Réessayez.';
export const MESSAGE_TROP_DE_FICHIERS = `Vous pouvez importer ${FICHIERS_MAX} fichiers maximum à la fois`;

/** US-12 CA vide, selon l'onglet. */
export const MESSAGES_VIDE: Record<Sens, string> = {
  ACHAT: 'Aucune facture fournisseur pour le moment',
  VENTE: 'Aucune facture client pour le moment',
};

/** US-53 : délai entre deux mises à jour de la liste tant qu'une lecture est en cours. */
export const DELAI_SUIVI_LECTURE_MS = 5000;

/** Un fichier refusé et son motif (US-52 RG2 : il n'empêche pas l'import des autres). */
export interface FichierRefuse {
  nom: string;
  motif: string;
}

interface ResultatImport {
  message: string;
  refus: FichierRefuse[];
}

function importees(nombre: number): string {
  return nombre > 1 ? `${nombre} factures importées` : `${nombre} facture importée`;
}

/** US-52 CA1 « 1 facture importée », CA2 motif du refus, lot mixte « 2 factures importées, 1 refusée ». */
export function resumeImport(nombre: number, refus: FichierRefuse[]): string {
  if (refus.length === 0) {
    return importees(nombre);
  }
  if (nombre === 0 && refus.length === 1) {
    return refus[0].motif;
  }
  const refusees = refus.length > 1 ? `${refus.length} refusées` : '1 refusée';
  return `${nombre === 0 ? 'Aucune facture importée' : importees(nombre)}, ${refusees}`;
}

/** US-51 : changement de statut en cours de saisie sur une ligne. */
interface Changement {
  factureId: number;
  statut: Statut;
  motif: string;
  erreur: string | null;
  envoi: boolean;
}

/**
 * US-12 Factures d'un onglet du dossier (US-11 RG2) ; US-51 statut de traitement de chaque facture ;
 * US-52 « Importer des factures » par le cabinet ; US-53 et US-54 statut de la lecture automatique
 * des factures fournisseurs et clients, suivi tout seul tant qu'une lecture est en cours. */
@Component({
  imports: [RouterLink],
  selector: 'app-factures-dossier',
  templateUrl: './factures-dossier.html',
  styleUrl: './factures-dossier.scss',
})
export class FacturesDossier implements OnChanges, OnDestroy {
  private readonly factures = inject(FactureService);
  readonly dossierId = input.required<number>();
  readonly sens = input.required<Sens>();
  protected readonly liste = signal<ListeFactures | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly statuts = STATUTS;
  protected readonly libelles = LIBELLES_STATUT;
  protected readonly pastilles = PASTILLES_STATUT;
  /** US-51 RG4 : filtre par statut ; null = tous les statuts. */
  protected readonly filtre = signal<Statut | null>(null);
  protected readonly changement = signal<Changement | null>(null);
  protected readonly affichees = computed(() => {
    const filtre = this.filtre();
    const factures = this.liste()?.factures ?? [];
    return filtre ? factures.filter((f) => f.statut === filtre) : factures;
  });
  protected readonly importEnCours = signal(false);
  protected readonly resultat = signal<ResultatImport | null>(null);
  protected readonly sources = SOURCES;
  protected readonly libellesLecture = LIBELLES_LECTURE;
  protected readonly pastillesLecture = PASTILLES_LECTURE;
  private suivi: ReturnType<typeof setTimeout> | null = null;

  ngOnChanges(): void {
    this.resultat.set(null);
    this.charger();
  }

  ngOnDestroy(): void {
    this.arreterSuivi();
  }

  private charger(): void {
    this.arreterSuivi();
    this.liste.set(null);
    this.erreur.set(null);
    this.changement.set(null);
    this.factures.lister(this.dossierId(), this.sens()).subscribe({
      next: (liste) => this.recevoir(liste),
      error: () => this.erreur.set(MESSAGE_INDISPONIBLE),
    });
  }

  private recevoir(liste: ListeFactures): void {
    this.liste.set(liste);
    if (liste.factures.some((f) => f.lecture === 'EN_COURS')) {
      this.suivi = setTimeout(() => this.suivreLecture(), DELAI_SUIVI_LECTURE_MS);
    }
  }

  /**
   * US-53 RG6 : la lecture se fait en arrière-plan ; la liste se met à jour toute seule, sans
   * « Chargement… », jusqu'à la fin des lectures. Pas pendant un changement de statut en cours de saisie.
   */
  private suivreLecture(): void {
    this.suivi = null;
    if (this.changement()) {
      this.suivi = setTimeout(() => this.suivreLecture(), DELAI_SUIVI_LECTURE_MS);
      return;
    }
    const dossierId = this.dossierId();
    const sens = this.sens();
    this.factures.lister(dossierId, sens).subscribe({
      next: (liste) => {
        if (this.dossierId() === dossierId && this.sens() === sens && !this.changement()) {
          this.recevoir(liste);
        }
      },
      // Échec passager : la liste affichée reste, nouvel essai plus tard.
      error: () => (this.suivi = setTimeout(() => this.suivreLecture(), DELAI_SUIVI_LECTURE_MS)),
    });
  }

  private arreterSuivi(): void {
    if (this.suivi) {
      clearTimeout(this.suivi);
      this.suivi = null;
    }
  }

  /** Bouton « Importer des factures ». */
  choisirFichiers(champ: HTMLInputElement): void {
    this.importer(Array.from(champ.files ?? []));
    champ.value = '';
  }

  /**
   * US-52 RG1 : import dans l'onglet ouvert (fournisseurs ou clients) ; RG2 : 20 fichiers au plus,
   * chaque fichier part seul et un refus n'empêche pas les autres. La liste est rechargée (RG4).
   */
  importer(fichiers: File[]): void {
    if (fichiers.length === 0 || this.importEnCours()) {
      return;
    }
    if (fichiers.length > FICHIERS_MAX) {
      this.resultat.set({ message: MESSAGE_TROP_DE_FICHIERS, refus: [] });
      return;
    }
    const dossierId = this.dossierId();
    const sens = this.sens();
    this.importEnCours.set(true);
    this.resultat.set(null);
    from(fichiers)
      .pipe(
        concatMap((fichier) => {
          const motif = refusAvantEnvoi(fichier);
          if (motif) {
            return of<FichierRefuse | null>({ nom: fichier.name, motif });
          }
          return this.factures.importer(dossierId, sens, fichier).pipe(
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
        const nombre = fichiers.length - refus.length;
        this.resultat.set({ message: resumeImport(nombre, refus), refus });
        this.importEnCours.set(false);
        if (nombre > 0 && this.sens() === sens && this.dossierId() === dossierId) {
          this.charger();
        }
      });
  }

  filtrer(valeur: string): void {
    this.filtre.set(STATUTS.includes(valeur as Statut) ? (valeur as Statut) : null);
  }

  /** US-51 CA4 : le menu ne propose que les passages autorisés ; une valeur vide annule. */
  choisirStatut(facture: LigneFacture, valeur: string): void {
    const statut = facture.statutsPossibles.find((s) => s === valeur);
    this.changement.set(
      statut ? { factureId: facture.id, statut, motif: '', erreur: null, envoi: false } : null,
    );
  }

  saisirMotif(motif: string): void {
    this.changement.update((c) => (c ? { ...c, motif, erreur: null } : c));
  }

  annulerChangement(): void {
    this.changement.set(null);
  }

  /** US-51 CA1, CA2 ; CA3 : « À compléter » sans motif -> « Indiquez le motif », rien n'est envoyé. */
  validerChangement(): void {
    const c = this.changement();
    if (!c || c.envoi) {
      return;
    }
    const motif = c.motif.trim();
    if (c.statut === 'A_COMPLETER' && !motif) {
      this.changement.set({ ...c, erreur: MESSAGE_MOTIF_MANQUANT });
      return;
    }
    this.changement.set({ ...c, envoi: true, erreur: null });
    this.factures
      .changerStatut(this.dossierId(), c.factureId, c.statut, c.statut === 'A_COMPLETER' ? motif : null)
      .subscribe({
        next: (ligne) => {
          this.liste.update((l) => (l ? { ...l, factures: l.factures.map((f) => (f.id === ligne.id ? ligne : f)) } : l));
          this.changement.set(null);
        },
        error: (err: HttpErrorResponse) => {
          const message =
            [400, 404, 409].includes(err.status) && err.error?.message ? String(err.error.message) : MESSAGE_INDISPONIBLE;
          this.changement.update((en) => (en ? { ...en, envoi: false, erreur: message } : en));
        },
      });
  }

  protected vide(): string {
    return MESSAGES_VIDE[this.sens()];
  }

  /** US-51 : filtre sans résultat. */
  protected videFiltre(): string {
    const filtre = this.filtre();
    return filtre ? `Aucune facture « ${LIBELLES_STATUT[filtre]} »` : '';
  }

  protected depot(facture: LigneFacture): string {
    return formaterDepot(facture.deposeeLe);
  }

  protected date(facture: LigneFacture): string {
    return dateOuTiret(facture.dateEmission);
  }

  protected ttc(facture: LigneFacture): string {
    return montantOuTiret(facture.montantTtc);
  }
}
