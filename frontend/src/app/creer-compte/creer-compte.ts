import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { Message } from '../connexion/connexion';
import { evaluerRegles } from '../mot-de-passe/regles';
import { SessionService } from '../session/session.service';
import { MISE_EN_PLACE_HT, euros, prixMensuel, trancheDe } from '../tarifs';
import { CabinetCree, InscriptionService } from './inscription.service';
import { Champ, Erreurs, Formulaire, erreursFormulaire } from './validation';

/** Le parcours a 2 étapes : le code reçu par email a été retiré. */
export type Etape = 'informations' | 'pret';

interface ErreurServeur {
  message?: string;
  erreurs?: Erreurs;
}

export const MESSAGE_INDISPONIBLE = 'La création du compte est momentanément impossible. Réessayez dans quelques instants.';

/** US-36 Connaître le tarif et créer le compte de son cabinet (écran « Créer un compte »). */
@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-creer-compte',
  styleUrl: './creer-compte.scss',
  templateUrl: './creer-compte.html',
})
export class CreerCompte {
  private readonly inscription = inject(InscriptionService);
  private readonly session = inject(SessionService);

  protected readonly form = new FormGroup({
    nomCabinet: new FormControl('', { nonNullable: true }),
    siren: new FormControl('', { nonNullable: true }),
    prenom: new FormControl('', { nonNullable: true }),
    nom: new FormControl('', { nonNullable: true }),
    email: new FormControl('', { nonNullable: true }),
    motDePasse: new FormControl('', { nonNullable: true }),
    nombreDossiers: new FormControl<number | null>(null),
    codeParrainage: new FormControl('', { nonNullable: true }),
    cguAcceptees: new FormControl(false, { nonNullable: true }),
  });

  private readonly valeurs = toSignal(this.form.valueChanges.pipe(map(() => this.valeur())), {
    initialValue: this.valeur(),
  });

  protected readonly etape = signal<Etape>('informations');
  protected readonly envoye = signal(false);
  protected readonly enCours = signal(false);
  protected readonly alerte = signal<Message | null>(null);
  protected readonly motDePasseVisible = signal(false);
  /** Erreurs renvoyées par le backend (ex. SIREN déjà utilisé), effacées à la saisie suivante. */
  private readonly erreursServeur = signal<Erreurs>({});

  protected readonly cabinet = signal<CabinetCree | null>(null);

  protected readonly erreurs = computed<Erreurs>(() => ({
    ...(this.envoye() ? erreursFormulaire(this.valeurs()) : {}),
    ...this.erreursServeur(),
  }));
  protected readonly regles = computed(() => evaluerRegles(this.valeurs().motDePasse));
  /** RG2 : le nombre de dossiers prévus affiche la tranche et le prix mensuel. */
  protected readonly tranche = computed(() => trancheDe(this.valeurs().nombreDossiers ?? 0));
  protected readonly prix = computed(() => prixMensuel(this.valeurs().nombreDossiers ?? 0));
  protected readonly miseEnPlace = euros(MISE_EN_PLACE_HT);
  protected readonly euros = euros;

  constructor() {
    // Nombre de dossiers saisi dans le simulateur de la landing page (?dossiers=120).
    const dossiers = Number(inject(ActivatedRoute).snapshot.queryParamMap.get('dossiers'));
    if (Number.isInteger(dossiers) && dossiers > 0) {
      this.form.controls.nombreDossiers.setValue(dossiers);
    }
    this.form.valueChanges
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => this.erreursServeur.set({}));
  }

  protected erreur(champ: Champ): string | undefined {
    return this.erreurs()[champ];
  }

  protected basculerMotDePasse(): void {
    this.motDePasseVisible.update((visible) => !visible);
  }

  /** Étape 1 « Vos informations » -> cabinet créé, étape 2 « Cabinet prêt » (CA1). */
  protected creerCabinet(): void {
    this.envoye.set(true);
    this.alerte.set(null);
    if (Object.keys(erreursFormulaire(this.valeur())).length > 0 || this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.inscription.creerCabinet(this.valeur()).subscribe({
      next: (cabinet) => {
        this.enCours.set(false);
        // L'administrateur est connecté : il arrive sur ses dossiers sans se reconnecter.
        this.session.ouvrir({
          jeton: cabinet.jeton,
          role: cabinet.role,
          nomAffiche: cabinet.nomAffiche,
          administrateur: true,
        });
        this.cabinet.set(cabinet);
        this.etape.set('pret');
      },
      error: (err: HttpErrorResponse) => {
        this.enCours.set(false);
        const corps = (err.error ?? {}) as ErreurServeur;
        // 400 : champs à corriger ; 409 : SIREN (CA2) ou email déjà utilisé.
        this.alerte.set({
          ton: 'erreur',
          texte: [400, 409].includes(err.status) && corps.message ? corps.message : MESSAGE_INDISPONIBLE,
        });
        this.erreursServeur.set(corps.erreurs ?? {});
      },
    });
  }

  private valeur(): Formulaire {
    return this.form.getRawValue();
  }
}
