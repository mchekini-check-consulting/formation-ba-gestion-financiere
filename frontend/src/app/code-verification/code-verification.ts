import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, OnDestroy, computed, inject, signal, viewChildren } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../connexion/auth.service';
import { Message } from '../connexion/connexion';
import { SessionService, accueil } from '../session/session.service';

/** Transmis par l'écran de connexion après le bon mot de passe. */
interface EtatCode {
  defi: string;
  emailMasque: string;
}

interface ErreurCode {
  message?: string;
  retourConnexion?: boolean;
  secondesRestantes?: number;
}

export const DELAI_RENVOI_S = 60;
const NB_CHIFFRES = 6;
export const MESSAGE_EMAIL_IMPOSSIBLE = 'L’email n’a pas pu être envoyé. Réessayez.';

/** US-04 Valider la connexion par un code reçu par email (maquette « Code de vérification »). */
@Component({
  imports: [RouterLink],
  selector: 'app-code-verification',
  styleUrl: './code-verification.scss',
  templateUrl: './code-verification.html',
})
export class CodeVerification implements OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly cases = viewChildren<ElementRef<HTMLInputElement>>('chiffre');

  private readonly defi: string | null;
  protected readonly indices = Array.from({ length: NB_CHIFFRES }, (_, i) => i);
  protected readonly chiffres = signal<string[]>(Array(NB_CHIFFRES).fill(''));
  protected readonly complet = computed(() => this.chiffres().every((c) => /^\d$/.test(c)));
  protected readonly emailMasque = signal('');
  protected readonly message = signal<Message | null>(null);
  protected readonly enCours = signal(false);
  protected readonly secondesAvantRenvoi = signal(DELAI_RENVOI_S);
  private minuteur: ReturnType<typeof setInterval> | undefined;

  constructor() {
    const etat = (history.state ?? {}) as Partial<EtatCode>;
    this.defi = etat.defi ?? null;
    if (!this.defi) {
      // Écran ouvert sans passer par le mot de passe (ou page rechargée).
      void this.router.navigate(['/connexion']);
      return;
    }
    this.emailMasque.set(etat.emailMasque ?? '');
    this.demarrerMinuteur(DELAI_RENVOI_S);
  }

  ngOnDestroy(): void {
    clearInterval(this.minuteur);
  }

  protected saisir(index: number, evenement: Event): void {
    const champ = evenement.target as HTMLInputElement;
    const chiffre = champ.value.replace(/\D/g, '').slice(-1);
    champ.value = chiffre;
    this.chiffres.update((liste) => liste.map((c, i) => (i === index ? chiffre : c)));
    if (chiffre && index < NB_CHIFFRES - 1) {
      this.focus(index + 1);
    }
  }

  protected toucher(index: number, evenement: KeyboardEvent): void {
    if (evenement.key === 'Backspace' && !this.chiffres()[index] && index > 0) {
      this.focus(index - 1);
    }
  }

  /** Coller le code reçu remplit les six cases. */
  protected coller(evenement: ClipboardEvent): void {
    const chiffres = (evenement.clipboardData?.getData('text') ?? '').replace(/\D/g, '').slice(0, NB_CHIFFRES);
    if (!chiffres) {
      return;
    }
    evenement.preventDefault();
    this.remplir(chiffres);
  }

  protected valider(): void {
    if (!this.defi || !this.complet() || this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.auth.validerCode(this.defi, this.chiffres().join('')).subscribe({
      next: (session) => {
        this.enCours.set(false);
        this.session.ouvrir(session);
        // US-03 RG3 : le collaborateur arrive sur ses dossiers, le dirigeant sur son espace client.
        void this.router.navigate([accueil(session.role)]);
      },
      error: (err: HttpErrorResponse) => {
        this.enCours.set(false);
        const corps = (err.error ?? {}) as ErreurCode;
        if (corps.retourConnexion) {
          void this.router.navigate(['/connexion'], { state: { message: corps.message } }); // RG3
          return;
        }
        // CA2 « Code incorrect. Il vous reste 2 tentatives. » ; CA3 « Ce code a expiré. »
        const ton = err.status === 410 ? 'avertissement' : 'erreur';
        this.message.set({ ton, texte: corps.message ?? 'La vérification est momentanément impossible. Réessayez.' });
        this.remplir('');
      },
    });
  }

  /** RG2 : nouveau code possible après 60 secondes ; il remplace l'ancien. */
  protected renvoyer(): void {
    if (!this.defi || this.secondesAvantRenvoi() > 0) {
      return;
    }
    this.auth.renvoyerCode(this.defi).subscribe({
      next: () => {
        this.message.set({ ton: 'succes', texte: 'Un nouveau code vous a été envoyé.' });
        this.remplir('');
        this.demarrerMinuteur(DELAI_RENVOI_S);
      },
      error: (err: HttpErrorResponse) => {
        const corps = (err.error ?? {}) as ErreurCode;
        if (corps.retourConnexion) {
          void this.router.navigate(['/connexion'], { state: { message: corps.message } });
        } else if (err.status === 429) {
          this.demarrerMinuteur(corps.secondesRestantes ?? DELAI_RENVOI_S);
        } else {
          this.message.set({ ton: 'erreur', texte: corps.message ?? MESSAGE_EMAIL_IMPOSSIBLE });
        }
      },
    });
  }

  private remplir(code: string): void {
    const chiffres = Array.from({ length: NB_CHIFFRES }, (_, i) => code[i] ?? '');
    this.chiffres.set(chiffres);
    this.cases().forEach((c, i) => (c.nativeElement.value = chiffres[i]));
    this.focus(Math.min(code.length, NB_CHIFFRES - 1));
  }

  private focus(index: number): void {
    this.cases()[index]?.nativeElement.focus();
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
