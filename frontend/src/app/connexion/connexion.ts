import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { SessionService, accueil } from '../session/session.service';
import { AuthService } from './auth.service';

/** Message unique, que l'email ou le mot de passe soit faux (US-03 RG2). */
export const MESSAGE_IDENTIFIANTS_INCORRECTS = 'Email ou mot de passe incorrect';
export const MESSAGE_SERVICE_INDISPONIBLE =
  'La connexion est momentanément impossible. Réessayez dans quelques instants.';

export type Ton = 'erreur' | 'avertissement' | 'succes' | 'neutre';

export interface Message {
  ton: Ton;
  texte: string;
}

/** Messages affichés en arrivant sur l'écran (?raison=...), maquette « Autres messages ». */
const MESSAGES_DE_RETOUR: Record<string, Message> = {
  deconnexion: { ton: 'succes', texte: 'Vous êtes déconnecté' },
  'session-expiree': { ton: 'neutre', texte: 'Votre session a expiré, reconnectez-vous' },
  'mot-de-passe-change': { ton: 'succes', texte: 'Votre mot de passe a été changé. Vous pouvez vous connecter.' },
};

type Champ = 'email' | 'motDePasse';

/** US-03 Se connecter (+ US-05 blocage) : écran « Connexion » commun aux cabinets et aux clients. */
@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-connexion',
  styleUrl: './connexion.scss',
  templateUrl: './connexion.html',
})
export class Connexion {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);

  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    motDePasse: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected readonly envoye = signal(false);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<Message | null>(null);
  protected readonly info = signal<Message | null>(null);
  protected readonly motDePasseVisible = signal(false);

  constructor() {
    const raison = inject(ActivatedRoute).snapshot.queryParamMap.get('raison');
    if (raison && MESSAGES_DE_RETOUR[raison]) {
      this.info.set(MESSAGES_DE_RETOUR[raison]);
    }
  }

  /** « Ce champ est obligatoire » : affiché seulement après un premier essai. */
  protected champVide(champ: Champ): boolean {
    return this.envoye() && this.form.controls[champ].invalid;
  }

  protected basculerMotDePasse(): void {
    this.motDePasseVisible.update((visible) => !visible);
  }

  protected seConnecter(): void {
    this.envoye.set(true);
    this.erreur.set(null);
    if (this.form.invalid || this.enCours()) {
      return;
    }
    const { email, motDePasse } = this.form.getRawValue();
    this.enCours.set(true);
    this.auth.seConnecter(email, motDePasse).subscribe({
      next: (session) => {
        this.enCours.set(false);
        this.session.ouvrir(session);
        // US-03 RG3 : le collaborateur arrive sur ses dossiers, le dirigeant sur son espace client.
        void this.router.navigate([accueil(session.role)]);
      },
      error: (err: HttpErrorResponse) => {
        this.enCours.set(false);
        this.info.set(null);
        this.erreur.set(Connexion.messageErreur(err));
        // Le mot de passe est vidé pour le ressaisir, sans afficher « obligatoire ».
        this.form.controls.motDePasse.reset();
        this.envoye.set(false);
      },
    });
  }

  private static messageErreur(err: HttpErrorResponse): Message {
    const texteServeur = (err.error as { message?: string } | null)?.message;
    switch (err.status) {
      case 401: // US-03 CA2 / RG2
        return { ton: 'erreur', texte: MESSAGE_IDENTIFIANTS_INCORRECTS };
      case 423: // US-05 CA1 : compte bloqué 15 minutes
        return { ton: 'avertissement', texte: texteServeur ?? 'Compte bloqué 15 minutes' };
      default:
        return { ton: 'erreur', texte: MESSAGE_SERVICE_INDISPONIBLE };
    }
  }
}
