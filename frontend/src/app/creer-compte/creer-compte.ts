import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnDestroy, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { Message } from '../connexion/connexion';
import { evaluerRegles } from '../mot-de-passe/regles';
import { SessionService } from '../session/session.service';
import { MISE_EN_PLACE_HT, euros, prixMensuel, trancheDe } from '../tarifs';
import { CabinetCree, InscriptionService } from './inscription.service';
import { Champ, Erreurs, Formulaire, erreursFormulaire } from './validation';

/** RG3 : le parcours a 3 étapes. */
export type Etape = 'informations' | 'code' | 'pret';

interface ErreurServeur {
  message?: string;
  erreurs?: Erreurs;
  retourFormulaire?: boolean;
  secondesRestantes?: number;
}

export const DELAI_RENVOI_S = 60;
export const MESSAGE_INDISPONIBLE = 'La création du compte est momentanément impossible. Réessayez dans quelques instants.';
/** Titre de l'onglet à l'étape 2 « Code email ». */
export const TITRE_CONFIRMATION = 'Pointéo – Confirmer l’email';

/**
 * Nombre de dossiers tapé au clavier : vide -> null (« obligatoire »), espaces ignorés (« 1 000 »),
 * autre chose que des chiffres -> NaN (« Indiquez au moins 1 dossier »).
 */
export function nombreSaisi(saisie: string): number | null {
  const chiffres = saisie.replace(/\s/g, '');
  if (!chiffres) {
    return null;
  }
  return /^\d+$/.test(chiffres) ? Number(chiffres) : Number.NaN;
}

/** US-36 Connaître le tarif et créer le compte de son cabinet (écran « Créer un compte »). */
@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-creer-compte',
  styleUrl: './creer-compte.scss',
  templateUrl: './creer-compte.html',
})
export class CreerCompte implements OnDestroy {
  private readonly inscription = inject(InscriptionService);
  private readonly session = inject(SessionService);
  private readonly titre = inject(Title);
  private readonly titreInitial = this.titre.getTitle();

  protected readonly form = new FormGroup({
    nomCabinet: new FormControl('', { nonNullable: true }),
    siren: new FormControl('', { nonNullable: true }),
    prenom: new FormControl('', { nonNullable: true }),
    nom: new FormControl('', { nonNullable: true }),
    email: new FormControl('', { nonNullable: true }),
    motDePasse: new FormControl('', { nonNullable: true }),
    /** Saisie libre au clavier (texte), convertie en nombre par valeur(). */
    nombreDossiers: new FormControl('', { nonNullable: true }),
    codeParrainage: new FormControl('', { nonNullable: true }),
    cguAcceptees: new FormControl(false, { nonNullable: true }),
  });
  protected readonly code = new FormControl('', { nonNullable: true });

  private readonly valeurs = toSignal(this.form.valueChanges.pipe(map(() => this.valeur())), {
    initialValue: this.valeur(),
  });
  private readonly codeSaisi = toSignal(this.code.valueChanges, { initialValue: '' });

  protected readonly etape = signal<Etape>('informations');
  protected readonly envoye = signal(false);
  protected readonly enCours = signal(false);
  protected readonly alerte = signal<Message | null>(null);
  protected readonly motDePasseVisible = signal(false);
  /** Erreurs renvoyées par le backend (ex. SIREN déjà utilisé), effacées à la saisie suivante. */
  private readonly erreursServeur = signal<Erreurs>({});

  private defi: string | null = null;
  /** Email saisi à l'étape 1, rappelé à l'étape 2 « Confirmez votre email ». */
  protected readonly emailSaisi = signal('');
  protected readonly secondesAvantRenvoi = signal(0);
  private minuteur: ReturnType<typeof setInterval> | undefined;

  protected readonly cabinet = signal<CabinetCree | null>(null);

  protected readonly erreurs = computed<Erreurs>(() => ({
    ...(this.envoye() ? erreursFormulaire(this.valeurs()) : {}),
    ...this.erreursServeur(),
  }));
  protected readonly regles = computed(() => evaluerRegles(this.valeurs().motDePasse));
  /** RG2 : le nombre de dossiers prévus affiche la tranche et le prix mensuel. */
  protected readonly tranche = computed(() => trancheDe(this.valeurs().nombreDossiers ?? 0));
  protected readonly prix = computed(() => prixMensuel(this.valeurs().nombreDossiers ?? 0));
  protected readonly codeComplet = computed(() => /^\d{6}$/.test(this.codeSaisi()));
  protected readonly miseEnPlace = euros(MISE_EN_PLACE_HT);
  protected readonly euros = euros;

  constructor() {
    // Nombre de dossiers saisi dans le simulateur de la landing page (?dossiers=120).
    const dossiers = Number(inject(ActivatedRoute).snapshot.queryParamMap.get('dossiers'));
    if (Number.isInteger(dossiers) && dossiers > 0) {
      this.form.controls.nombreDossiers.setValue(String(dossiers));
    }
    this.form.valueChanges
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => this.erreursServeur.set({}));
  }

  ngOnDestroy(): void {
    clearInterval(this.minuteur);
    this.titre.setTitle(this.titreInitial);
  }

  protected erreur(champ: Champ): string | undefined {
    return this.erreurs()[champ];
  }

  protected basculerMotDePasse(): void {
    this.motDePasseVisible.update((visible) => !visible);
  }

  /** Étape 1 « Vos informations » -> envoi du code par email. */
  protected envoyerFormulaire(): void {
    this.envoye.set(true);
    this.alerte.set(null);
    if (Object.keys(erreursFormulaire(this.valeur())).length > 0 || this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.inscription.envoyerFormulaire(this.valeur()).subscribe({
      next: (reponse) => {
        this.enCours.set(false);
        this.defi = reponse.defi;
        this.emailSaisi.set(this.valeur().email.trim());
        this.code.reset();
        this.allerA('code');
        this.demarrerMinuteur(DELAI_RENVOI_S);
      },
      error: (err: HttpErrorResponse) => {
        this.enCours.set(false);
        const corps = (err.error ?? {}) as ErreurServeur;
        // 400 : champs à corriger ; 409 : SIREN (CA2) ou email déjà utilisé ; 503 : email impossible.
        this.alerte.set({
          ton: 'erreur',
          texte: [400, 409, 503].includes(err.status) && corps.message ? corps.message : MESSAGE_INDISPONIBLE,
        });
        this.erreursServeur.set(corps.erreurs ?? {});
      },
    });
  }

  /** Étape 2 « Code email » -> cabinet créé, étape 3 « Cabinet prêt » (CA1). */
  protected validerCode(): void {
    if (!this.defi || !this.codeComplet() || this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.inscription.validerCode(this.defi, this.code.value).subscribe({
      next: (cabinet) => {
        this.enCours.set(false);
        clearInterval(this.minuteur);
        // L'administrateur est connecté : il arrive sur ses dossiers sans se reconnecter.
        this.session.ouvrir({
          jeton: cabinet.jeton,
          role: cabinet.role,
          nomAffiche: cabinet.nomAffiche,
          administrateur: true,
        });
        this.cabinet.set(cabinet);
        this.alerte.set(null);
        this.allerA('pret');
      },
      error: (err: HttpErrorResponse) => {
        this.enCours.set(false);
        const corps = (err.error ?? {}) as ErreurServeur;
        if (corps.retourFormulaire) {
          this.revenirAuFormulaire({ ton: 'erreur', texte: corps.message ?? MESSAGE_INDISPONIBLE });
          return;
        }
        const ton = err.status === 410 ? 'avertissement' : 'erreur';
        this.alerte.set({ ton, texte: corps.message ?? MESSAGE_INDISPONIBLE });
        this.code.reset();
      },
    });
  }

  /** Nouveau code possible après 60 secondes ; il remplace l'ancien. */
  protected renvoyerCode(): void {
    if (!this.defi || this.secondesAvantRenvoi() > 0) {
      return;
    }
    this.inscription.renvoyerCode(this.defi).subscribe({
      next: () => {
        this.alerte.set({ ton: 'succes', texte: 'Un nouveau code vous a été envoyé.' });
        this.code.reset();
        this.demarrerMinuteur(DELAI_RENVOI_S);
      },
      error: (err: HttpErrorResponse) => {
        const corps = (err.error ?? {}) as ErreurServeur;
        if (corps.retourFormulaire) {
          this.revenirAuFormulaire({ ton: 'erreur', texte: corps.message ?? MESSAGE_INDISPONIBLE });
        } else if (err.status === 429) {
          this.demarrerMinuteur(corps.secondesRestantes ?? DELAI_RENVOI_S);
        } else {
          this.alerte.set({ ton: 'erreur', texte: corps.message ?? MESSAGE_INDISPONIBLE });
        }
      },
    });
  }

  /** « Modifier l’email » : retour à l'étape 1, formulaire conservé. */
  protected revenirAuFormulaire(message: Message | null = null): void {
    clearInterval(this.minuteur);
    this.defi = null;
    this.alerte.set(message);
    this.allerA('informations');
  }

  /** Change d'étape ; l'onglet s'appelle « Pointéo – Confirmer l’email » pendant l'étape 2. */
  private allerA(etape: Etape): void {
    this.etape.set(etape);
    this.titre.setTitle(etape === 'code' ? TITRE_CONFIRMATION : this.titreInitial);
  }

  private valeur(): Formulaire {
    const brut = this.form.getRawValue();
    return { ...brut, nombreDossiers: nombreSaisi(brut.nombreDossiers) };
  }

  private demarrerMinuteur(secondes: number): void {
    clearInterval(this.minuteur);
    this.secondesAvantRenvoi.set(secondes);
    this.minuteur = setInterval(() => {
      this.secondesAvantRenvoi.update((s) => Math.max(0, s - 1));
      if (this.secondesAvantRenvoi() === 0) {
        clearInterval(this.minuteur);
      }
    }, 1000);
  }
}
