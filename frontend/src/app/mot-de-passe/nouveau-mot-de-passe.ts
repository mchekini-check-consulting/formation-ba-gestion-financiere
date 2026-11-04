import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../connexion/auth.service';
import { MESSAGE_SERVICE_INDISPONIBLE } from '../connexion/connexion';
import { EtapesMotDePasse } from './etapes';
import { evaluerRegles } from './regles';

export const MESSAGE_LIEN_EXPIRE = 'Ce lien a expiré, demandez-en un nouveau';

/** US-06 étape 2 : nouveau mot de passe, depuis le lien reçu par email. */
@Component({
  imports: [ReactiveFormsModule, RouterLink, EtapesMotDePasse],
  selector: 'app-nouveau-mot-de-passe',
  styleUrl: './mot-de-passe.scss',
  template: `
    <div class="page">
      <app-etapes-mot-de-passe [etape]="2" />
      <main class="main">
        <form class="form" (submit)="$event.preventDefault(); valider()" novalidate>
          <span class="etape">Étape 2 · depuis le lien reçu</span>
          <h1>Nouveau mot de passe</h1>

          @switch (etat()) {
            @case ('verification') {
              <p class="hint">Vérification du lien…</p>
            }
            @case ('expire') {
              <!-- CA2 -->
              <div role="alert" class="alert alert--avertissement">
                <span>{{ messageExpire }}</span>
                <a routerLink="/mot-de-passe-oublie">Faire une nouvelle demande</a>
              </div>
            }
            @case ('valide') {
              <div class="field">
                <label for="nouveau">Nouveau mot de passe</label>
                <input id="nouveau" type="password" autocomplete="new-password" maxlength="64"
                  [formControl]="motDePasse" />
              </div>
              <div class="meter" aria-hidden="true">
                @for (barre of [1, 2, 3, 4]; track barre) {
                  <span [class.on]="barre <= force()"></span>
                }
              </div>
              <!-- CA3 : les règles non respectées s'affichent -->
              <ul class="rules" aria-label="Règles du mot de passe">
                @for (regle of regles(); track regle.libelle) {
                  <li [class.ok]="regle.respectee" [class.ko]="!regle.respectee">
                    {{ regle.respectee ? '✓' : '✗' }} {{ regle.libelle }}
                  </li>
                }
              </ul>
              <div class="field">
                <label for="confirmation">Confirmer le mot de passe</label>
                <input id="confirmation" type="password" autocomplete="new-password" maxlength="64"
                  [formControl]="confirmation" [class.invalid]="confirmationDifferente()" />
                @if (confirmationDifferente()) {
                  <span class="field__error">Les deux mots de passe ne correspondent pas</span>
                }
              </div>
              @if (erreur(); as texte) {
                <div role="alert" class="alert">{{ texte }}</div>
              }
              <button type="submit" class="submit" [disabled]="!peutValider()">Valider</button>
            }
          }
          <a routerLink="/connexion" class="back">← Retour à la connexion</a>
        </form>
      </main>
    </div>
  `,
})
export class NouveauMotDePasse {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly jeton = inject(ActivatedRoute).snapshot.queryParamMap.get('jeton');

  protected readonly messageExpire = MESSAGE_LIEN_EXPIRE;
  protected readonly etat = signal<'verification' | 'valide' | 'expire'>('verification');
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);

  protected readonly motDePasse = new FormControl('', { nonNullable: true });
  protected readonly confirmation = new FormControl('', { nonNullable: true });
  private readonly valeur = toSignal(this.motDePasse.valueChanges, { initialValue: '' });
  private readonly valeurConfirmation = toSignal(this.confirmation.valueChanges, { initialValue: '' });

  protected readonly regles = computed(() => evaluerRegles(this.valeur()));
  protected readonly force = computed(() => {
    const respectees = this.regles().filter((r) => r.respectee).length;
    return this.valeur() ? Math.floor((respectees * 4) / this.regles().length) : 0;
  });
  protected readonly confirmationDifferente = computed(
    () => this.valeurConfirmation() !== '' && this.valeurConfirmation() !== this.valeur(),
  );
  protected readonly peutValider = computed(
    () =>
      this.regles().every((r) => r.respectee) &&
      this.valeurConfirmation() === this.valeur() &&
      !this.enCours(),
  );

  constructor() {
    if (!this.jeton) {
      this.etat.set('expire');
      return;
    }
    this.auth.verifierLien(this.jeton).subscribe({
      next: () => this.etat.set('valide'),
      error: () => this.etat.set('expire'),
    });
  }

  protected valider(): void {
    if (!this.jeton || !this.peutValider()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    this.auth.reinitialiser(this.jeton, this.motDePasse.value).subscribe({
      next: () => {
        // CA1 : mot de passe changé, il peut se connecter.
        void this.router.navigate(['/connexion'], { queryParams: { raison: 'mot-de-passe-change' } });
      },
      error: (err: HttpErrorResponse) => {
        this.enCours.set(false);
        if (err.status === 410) {
          this.etat.set('expire');
          return;
        }
        const corps = (err.error ?? {}) as { message?: string; reglesNonRespectees?: string[] };
        this.erreur.set(
          corps.reglesNonRespectees?.length
            ? `${corps.message} : ${corps.reglesNonRespectees.join(', ')}`
            : MESSAGE_SERVICE_INDISPONIBLE,
        );
      },
    });
  }
}
