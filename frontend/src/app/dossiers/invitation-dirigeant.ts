import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, Injectable, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { apiBase } from '../api';
import { EtatEspaceClient } from './dossier.service';

/** US-14 RG1 : formulaire « Inviter le dirigeant ». */
export interface DemandeInvitationDirigeant {
  prenom: string;
  nom: string;
  email: string;
}

/** Réponse d'un envoi : nouvel état de l'espace client ; jetonDeTest en mode test seulement. */
export interface InvitationEnvoyee {
  etatEspaceClient: EtatEspaceClient;
  jetonDeTest: string | null;
}

type Champ = keyof DemandeInvitationDirigeant;

/** CA erreur. */
export const EMAIL_INVALIDE = 'Saisissez une adresse email valide';
export const MESSAGE_RENVOI = "Une nouvelle invitation a été envoyée. L'ancien lien ne fonctionne plus.";
const MESSAGE_INDISPONIBLE = 'Le service est momentanément indisponible. Réessayez.';
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** US-14 Invitation du dirigeant : appels au backend (via le gateway). */
@Injectable({ providedIn: 'root' })
export class InvitationDirigeantService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase()}/dossiers`;

  inviter(dossierId: number, demande: DemandeInvitationDirigeant): Observable<InvitationEnvoyee> {
    return this.http.post<InvitationEnvoyee>(`${this.url}/${dossierId}/invitation`, demande);
  }

  /** RG2 : nouveau lien, l'ancien est annulé. */
  renvoyer(dossierId: number): Observable<InvitationEnvoyee> {
    return this.http.post<InvitationEnvoyee>(`${this.url}/${dossierId}/invitation/renvoi`, null);
  }
}

/** Mêmes règles que le backend : vérifiées avant l'envoi pour répondre tout de suite. */
export function erreursInvitationDirigeant(demande: DemandeInvitationDirigeant): Partial<Record<Champ, string>> {
  const erreurs: Partial<Record<Champ, string>> = {};
  if (!demande.prenom.trim()) {
    erreurs.prenom = 'Ce champ est obligatoire';
  }
  if (!demande.nom.trim()) {
    erreurs.nom = 'Ce champ est obligatoire';
  }
  if (!EMAIL.test(demande.email.trim())) {
    erreurs.email = EMAIL_INVALIDE;
  }
  return erreurs;
}

/**
 * US-14 Inviter le dirigeant, dans l'en-tête du dossier : « Inviter le dirigeant » quand
 * l'espace client n'est pas encore ouvert, « Renvoyer l'invitation » quand il est invité (RG3).
 */
@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-invitation-dirigeant',
  templateUrl: './invitation-dirigeant.html',
  styleUrl: './invitation-dirigeant.scss',
})
export class InvitationDirigeant {
  private readonly invitations = inject(InvitationDirigeantService);
  readonly dossierId = input.required<number>();
  readonly etat = input.required<EtatEspaceClient>();
  /** RG3 : Non invité -> Invité. */
  readonly invite = output<EtatEspaceClient>();

  protected readonly formulaire = inject(FormBuilder).nonNullable.group({ prenom: '', nom: '', email: '' });
  protected readonly ouvert = signal(false);
  protected readonly erreurs = signal<Partial<Record<Champ, string>>>({});
  protected readonly alerte = signal<string | null>(null);
  protected readonly succes = signal<string | null>(null);
  protected readonly lienDeTest = signal<string | null>(null);
  protected readonly enCours = signal(false);

  ouvrir(): void {
    this.ouvert.set(true);
    this.succes.set(null);
    this.lienDeTest.set(null);
  }

  annuler(): void {
    this.ouvert.set(false);
    this.erreurs.set({});
    this.alerte.set(null);
    this.formulaire.reset();
  }

  inviter(): void {
    if (this.enCours()) {
      return;
    }
    const demande = this.formulaire.getRawValue();
    const erreurs = erreursInvitationDirigeant(demande);
    this.erreurs.set(erreurs);
    if (Object.keys(erreurs).length > 0) {
      this.alerte.set(null);
      return;
    }
    this.envoyer(this.invitations.inviter(this.dossierId(), demande), `Invitation envoyée à ${demande.email.trim()}`);
  }

  renvoyer(): void {
    if (!this.enCours()) {
      this.envoyer(this.invitations.renvoyer(this.dossierId()), MESSAGE_RENVOI);
    }
  }

  private envoyer(appel: Observable<InvitationEnvoyee>, message: string): void {
    this.enCours.set(true);
    this.alerte.set(null);
    appel.subscribe({
      next: ({ etatEspaceClient, jetonDeTest }) => {
        this.enCours.set(false);
        this.ouvert.set(false);
        this.formulaire.reset();
        this.succes.set(message);
        this.lienDeTest.set(jetonDeTest ? `/activer-compte?jeton=${jetonDeTest}` : null);
        this.invite.emit(etatEspaceClient);
      },
      error: (err: HttpErrorResponse) => {
        this.enCours.set(false);
        this.erreurs.set(err.error?.erreurs ?? {});
        this.alerte.set(err.error?.message ?? MESSAGE_INDISPONIBLE);
      },
    });
  }
}
