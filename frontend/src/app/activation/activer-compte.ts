import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, Injectable, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { apiBase } from '../api';
import { Role, SessionOuverte } from '../connexion/auth.service';
import { MESSAGE_SERVICE_INDISPONIBLE } from '../connexion/connexion';
import { evaluerRegles } from '../mot-de-passe/regles';
import { SessionService, accueil } from '../session/session.service';

/** US-08 : messages exacts. */
export const MESSAGE_INVITATION_EXPIREE = 'Cette invitation a expiré. Demandez à votre cabinet de vous la renvoyer.';
export const MESSAGE_DEJA_ACTIVE = 'Votre espace est déjà activé';
export const MESSAGE_CGU = "Vous devez accepter les conditions d'utilisation";

/** Personne invitée, d'après le lien. */
export interface Invite {
  nomAffiche: string;
  role: Role;
}

/** US-08 Activation : appels au backend (via le gateway). Le jeton passe dans le corps, jamais dans l'adresse. */
@Injectable({ providedIn: 'root' })
export class ActivationService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase()}/auth/activation`;

  verifier(jeton: string): Observable<Invite> {
    return this.http.post<Invite>(`${this.url}/verifier`, { jeton });
  }

  activer(jeton: string, motDePasse: string, cguAcceptees: boolean): Observable<SessionOuverte> {
    return this.http.post<SessionOuverte>(this.url, { jeton, motDePasse, cguAcceptees });
  }
}

type Etat = 'verification' | 'formulaire' | 'expiree' | 'deja-active';

/**
 * US-08 Activer son compte, depuis le lien d'invitation (dirigeant invité par son cabinet,
 * collaborateur invité par l'administrateur). RG2 : mot de passe et CGU, pas de mobile.
 * RG3 : compte actif dès la validation, sans code ; redirection vers l'écran d'accueil du rôle.
 */
@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-activer-compte',
  styleUrl: './activer-compte.scss',
  template: `
    <div class="page">
      <aside class="aside aside--top">
        <a routerLink="/" class="brand">
          <svg width="30" height="30" viewBox="0 0 30 30" aria-hidden="true">
            <rect width="30" height="30" rx="9" fill="#8C6B36"></rect>
            <path d="M9 15.5l4.2 4.2L21.5 11" fill="none" stroke="#FFFDF9" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"></path>
          </svg>
          <span>Pointéo</span>
        </a>
        <ol class="steps">
          <li>
            <span class="steps__num">1</span>
            <span class="steps__texte">
              <strong>Votre cabinet vous a invité</strong>
              <span>Le lien est valable 7 jours, une seule fois.</span>
            </span>
          </li>
          <li>
            <span class="steps__num steps__num--actif">2</span>
            <span class="steps__texte">
              <strong>Choisissez votre mot de passe</strong>
              <span>Votre espace est actif dès la validation.</span>
            </span>
          </li>
        </ol>
      </aside>
      <main class="main">
        <form class="form" (submit)="$event.preventDefault(); valider()" novalidate>
          <span class="etape">Depuis le lien d'invitation</span>
          <h1>Activer votre espace</h1>

          @switch (etat()) {
            @case ('verification') {
              <p class="hint">Vérification du lien…</p>
            }
            @case ('expiree') {
              <div role="alert" class="alert alert--avertissement">{{ messageExpiree }}</div>
            }
            @case ('deja-active') {
              <div role="alert" class="alert alert--neutre">
                <span>{{ messageDejaActive }}</span>
                <a routerLink="/connexion">Se connecter</a>
              </div>
            }
            @case ('formulaire') {
              @if (invite(); as personne) {
                <p class="hint">Bienvenue {{ personne.nomAffiche }}</p>
              }
              <div class="field">
                <label for="mot-de-passe">Mot de passe</label>
                <input id="mot-de-passe" type="password" autocomplete="new-password" maxlength="64"
                  [formControl]="motDePasse" />
              </div>
              <div class="meter" aria-hidden="true">
                @for (barre of [1, 2, 3, 4]; track barre) {
                  <span [class.on]="barre <= force()"></span>
                }
              </div>
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
              <div class="field">
                <label class="cgu">
                  <input id="cgu" type="checkbox" [formControl]="cgu" [attr.aria-invalid]="!!erreurCgu()" />
                  <span>J'accepte les <a routerLink="/cgu" target="_blank">conditions générales d'utilisation</a></span>
                </label>
                @if (erreurCgu(); as e) {
                  <span class="field__error">{{ e }}</span>
                }
              </div>
              @if (erreur(); as texte) {
                <div role="alert" class="alert">{{ texte }}</div>
              }
              <button type="submit" class="submit" [disabled]="!peutValider()">
                {{ enCours() ? 'Activation…' : 'Activer mon espace' }}
              </button>
            }
          }
        </form>
      </main>
    </div>
  `,
})
export class ActiverCompte {
  private readonly activation = inject(ActivationService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly jeton = inject(ActivatedRoute).snapshot.queryParamMap.get('jeton');

  protected readonly messageExpiree = MESSAGE_INVITATION_EXPIREE;
  protected readonly messageDejaActive = MESSAGE_DEJA_ACTIVE;
  protected readonly etat = signal<Etat>('verification');
  protected readonly invite = signal<Invite | null>(null);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurCgu = signal<string | null>(null);

  protected readonly motDePasse = new FormControl('', { nonNullable: true });
  protected readonly confirmation = new FormControl('', { nonNullable: true });
  protected readonly cgu = new FormControl(false, { nonNullable: true });
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
    () => this.regles().every((r) => r.respectee) && this.valeurConfirmation() === this.valeur() && !this.enCours(),
  );

  constructor() {
    if (!this.jeton) {
      this.etat.set('expiree');
      return;
    }
    this.activation.verifier(this.jeton).subscribe({
      next: (invite) => {
        this.invite.set(invite);
        this.etat.set('formulaire');
      },
      error: (err: HttpErrorResponse) => this.refuser(err),
    });
    this.cgu.valueChanges.subscribe((acceptees) => {
      if (acceptees) {
        this.erreurCgu.set(null);
      }
    });
  }

  protected valider(): void {
    if (!this.jeton || !this.peutValider()) {
      return;
    }
    // RG2 : les CGU sont obligatoires.
    if (!this.cgu.value) {
      this.erreurCgu.set(MESSAGE_CGU);
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    this.activation.activer(this.jeton, this.motDePasse.value, true).subscribe({
      next: (session) => {
        // RG3 : actif aussitôt, session ouverte ; dirigeant -> espace client, collaborateur -> dossiers.
        this.session.ouvrir(session);
        void this.router.navigateByUrl(accueil(session.role));
      },
      error: (err: HttpErrorResponse) => {
        this.enCours.set(false);
        if (err.status === 400) {
          const corps = (err.error ?? {}) as { erreurs?: Record<string, string>; reglesNonRespectees?: string[] };
          this.erreurCgu.set(corps.erreurs?.['cgu'] ?? null);
          this.erreur.set(
            corps.reglesNonRespectees?.length
              ? `${corps.erreurs?.['motDePasse']} : ${corps.reglesNonRespectees.join(', ')}`
              : null,
          );
          return;
        }
        this.refuser(err);
      },
    });
  }

  private refuser(err: HttpErrorResponse): void {
    if (err.status === 410) {
      this.etat.set('expiree');
    } else if (err.status === 409) {
      this.etat.set('deja-active');
    } else {
      this.etat.set('formulaire');
      this.erreur.set(MESSAGE_SERVICE_INDISPONIBLE);
    }
  }
}
