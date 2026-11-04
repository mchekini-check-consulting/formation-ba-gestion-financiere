import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from './auth.service';

/** Message unique, que l'email ou le mot de passe soit faux (RG2). */
export const MESSAGE_IDENTIFIANTS_INCORRECTS = 'Email ou mot de passe incorrect';
export const MESSAGE_SERVICE_INDISPONIBLE =
  'La connexion est momentanément impossible. Réessayez dans quelques instants.';

type Champ = 'email' | 'motDePasse';

/** US-03 Se connecter : écran « Connexion » commun aux cabinets et aux clients. */
@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-connexion',
  styleUrl: './connexion.scss',
  templateUrl: './connexion.html',
})
export class Connexion {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    motDePasse: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected readonly envoye = signal(false);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly motDePasseVisible = signal(false);

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
      next: (reponse) => {
        this.enCours.set(false);
        // CA1 : l'écran du code SMS s'affiche (US-04) ; le rôle servira à choisir
        // l'écran d'arrivée après le code (RG3).
        void this.router.navigate(['/verification-code'], { state: { role: reponse.role } });
      },
      error: (err: HttpErrorResponse) => {
        this.enCours.set(false);
        // CA2 / RG2 : même message, que l'email ou le mot de passe soit faux.
        this.erreur.set(err.status === 401 ? MESSAGE_IDENTIFIANTS_INCORRECTS : MESSAGE_SERVICE_INDISPONIBLE);
        // Le mot de passe est vidé pour le ressaisir, sans afficher « obligatoire ».
        this.form.controls.motDePasse.reset();
        this.envoye.set(false);
      },
    });
  }
}
