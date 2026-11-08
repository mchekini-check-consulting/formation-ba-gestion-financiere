import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { DemandeDossier, DossierService } from './dossier.service';

type Champ = keyof DemandeDossier;

const MESSAGE_INDISPONIBLE = 'Le service est momentanément indisponible. Réessayez.';

/**
 * US-10 Créer un dossier client : raison sociale et SIREN (RG1) ; le SIREN est unique dans le
 * cabinet (RG2, vérifié par le backend) ; une fois créé, le dossier s'ouvre (RG3).
 */
@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-creer-dossier',
  styleUrl: './creer-dossier.scss',
  templateUrl: './creer-dossier.html',
})
export class CreerDossier {
  private readonly dossiers = inject(DossierService);
  private readonly router = inject(Router);

  protected readonly formulaire = inject(FormBuilder).nonNullable.group({ raisonSociale: '', siren: '' });
  protected readonly erreurs = signal<Partial<Record<Champ, string>>>({});
  protected readonly alerte = signal<string | null>(null);
  protected readonly enCours = signal(false);

  protected creer(): void {
    if (this.enCours()) {
      return;
    }
    const demande = this.formulaire.getRawValue();
    const erreurs = erreursDossier(demande);
    this.erreurs.set(erreurs);
    if (Object.keys(erreurs).length > 0) {
      this.alerte.set('Certains champs sont à corriger');
      return;
    }
    this.alerte.set(null);
    this.enCours.set(true);
    this.dossiers.creer(demande).subscribe({
      next: (dossier) => {
        this.enCours.set(false);
        void this.router.navigate(['/dossiers', dossier.id]);
      },
      error: (err: HttpErrorResponse) => {
        this.enCours.set(false);
        this.erreurs.set(err.error?.erreurs ?? {});
        this.alerte.set(err.error?.message ?? MESSAGE_INDISPONIBLE);
      },
    });
  }
}

const SEPARATEURS = /[\s.-]/g;

/** Mêmes règles que le backend (ValidationDossier.java) : vérifiées avant l'envoi. */
export function erreursDossier(demande: DemandeDossier): Partial<Record<Champ, string>> {
  const erreurs: Partial<Record<Champ, string>> = {};
  const raisonSociale = demande.raisonSociale.trim();
  if (!raisonSociale) {
    erreurs.raisonSociale = 'Ce champ est obligatoire';
  } else if (raisonSociale.length > 200) {
    erreurs.raisonSociale = 'Ce champ est trop long';
  }
  const siren = demande.siren.replace(SEPARATEURS, '');
  if (!siren) {
    erreurs.siren = 'Ce champ est obligatoire';
  } else if (!/^\d{9}$/.test(siren)) {
    erreurs.siren = 'Le SIREN comporte 9 chiffres';
  }
  return erreurs;
}
