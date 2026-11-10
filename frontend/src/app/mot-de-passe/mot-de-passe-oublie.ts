import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../connexion/auth.service';
import { MESSAGE_SERVICE_INDISPONIBLE } from '../connexion/connexion';
import { EtapesMotDePasse } from './etapes';

/** US-06 étape 1 : demander un lien de réinitialisation par email. */
@Component({
  imports: [ReactiveFormsModule, RouterLink, EtapesMotDePasse],
  selector: 'app-mot-de-passe-oublie',
  styleUrl: './mot-de-passe.scss',
  template: `
    <div class="page">
      <app-etapes-mot-de-passe [etape]="1" />
      <main class="main">
        <form class="form" (submit)="$event.preventDefault(); envoyer()" novalidate>
          <span class="etape">Étape 1</span>
          <h1>Mot de passe oublié</h1>
          <div class="field">
            <label for="email">Email</label>
            <input id="email" type="email" autocomplete="username" [formControl]="email"
              [class.invalid]="envoye() && email.invalid" />
            @if (envoye() && email.invalid) {
              <span class="field__error">Ce champ est obligatoire</span>
            }
          </div>
          <button type="submit" class="submit" [disabled]="enCours()">Envoyer le lien</button>

          @if (confirmation(); as texte) {
            <!-- RG2 : même message que l'email existe ou non -->
            <div role="status" class="alert alert--succes">{{ texte }}</div>
          }
          @if (erreur(); as texte) {
            <div role="alert" class="alert">{{ texte }}</div>
          }
          <a routerLink="/connexion" class="back">← Retour à la connexion</a>
        </form>
      </main>
    </div>
  `,
})
export class MotDePasseOublie {
  private readonly auth = inject(AuthService);

  protected readonly email = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  protected readonly envoye = signal(false);
  protected readonly enCours = signal(false);
  protected readonly confirmation = signal<string | null>(null);
  protected readonly erreur = signal<string | null>(null);

  protected envoyer(): void {
    this.envoye.set(true);
    this.erreur.set(null);
    if (this.email.invalid || this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.auth.demanderReinitialisation(this.email.value).subscribe({
      next: (reponse) => {
        this.enCours.set(false);
        this.confirmation.set(reponse.message);
      },
      error: () => {
        this.enCours.set(false);
        this.erreur.set(MESSAGE_SERVICE_INDISPONIBLE);
      },
    });
  }
}
