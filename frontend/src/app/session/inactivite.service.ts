import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../connexion/auth.service';
import { SessionService } from './session.service';

/** US-07 RG2 : 30 minutes sans activité ; avertissement 2 minutes avant. */
export const DUREE_INACTIVITE_MS = 30 * 60_000;
export const AVERTISSEMENT_MS = 2 * 60_000;
/** Pendant que la personne travaille, le backend est prévenu au plus toutes les 5 minutes. */
export const SIGNAL_ACTIVITE_MS = 5 * 60_000;

const EVENEMENTS = ['click', 'keydown', 'mousemove', 'scroll', 'touchstart'] as const;

@Injectable({ providedIn: 'root' })
export class InactiviteService {
  private readonly auth = inject(AuthService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);

  /** Secondes avant l'expiration pendant l'avertissement, null sinon. */
  readonly secondesRestantes = signal<number | null>(null);

  private derniereActivite = 0;
  private dernierSignal = 0;
  private minuteur: ReturnType<typeof setInterval> | undefined;
  private readonly surActivite = () => this.activite();

  demarrer(): void {
    this.arreter();
    this.derniereActivite = Date.now();
    this.dernierSignal = Date.now();
    EVENEMENTS.forEach((e) => this.document.addEventListener(e, this.surActivite, { passive: true }));
    this.minuteur = setInterval(() => this.verifier(), 1000);
  }

  arreter(): void {
    clearInterval(this.minuteur);
    this.minuteur = undefined;
    EVENEMENTS.forEach((e) => this.document.removeEventListener(e, this.surActivite));
    this.secondesRestantes.set(null);
  }

  /** Bouton « Rester connecté » de l'avertissement. */
  resterConnecte(): void {
    this.secondesRestantes.set(null);
    this.derniereActivite = Date.now();
    this.signalerActivite();
  }

  /** Une fois l'avertissement affiché, seul « Rester connecté » prolonge la session. */
  activite(): void {
    if (this.secondesRestantes() !== null) {
      return;
    }
    this.derniereActivite = Date.now();
    if (Date.now() - this.dernierSignal >= SIGNAL_ACTIVITE_MS) {
      this.signalerActivite();
    }
  }

  verifier(): void {
    const restant = DUREE_INACTIVITE_MS - (Date.now() - this.derniereActivite);
    if (restant <= 0) {
      this.expirer();
    } else if (restant <= AVERTISSEMENT_MS) {
      this.secondesRestantes.set(Math.ceil(restant / 1000));
    }
  }

  private signalerActivite(): void {
    this.dernierSignal = Date.now();
    this.auth.session().subscribe({
      error: (erreur: HttpErrorResponse) => {
        if (erreur.status === 401) {
          this.expirer();
        }
      },
    });
  }

  /** RG3 : retour à l'écran de connexion avec « Votre session a expiré ». */
  private expirer(): void {
    const jeton = this.session.jeton();
    this.arreter();
    this.auth.deconnecter(jeton).subscribe({ error: () => undefined });
    this.session.fermer();
    void this.router.navigate(['/connexion'], { queryParams: { raison: 'session-expiree' } });
  }
}
