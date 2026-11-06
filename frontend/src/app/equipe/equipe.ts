import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DemandeInvitation, EquipeService, Membre, ROLES, RoleCabinet } from './equipe.service';

type Champ = keyof DemandeInvitation;

const MESSAGE_INDISPONIBLE = 'Le service est momentanément indisponible. Réessayez.';

/**
 * US-19 Inviter un collaborateur avec un rôle : liste de l'équipe du cabinet (actifs et
 * invités, CA1) et formulaire d'invitation. Page réservée aux administrateurs (RG1).
 */
@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-equipe',
  styleUrl: './equipe.scss',
  templateUrl: './equipe.html',
})
export class Equipe implements OnInit {
  private readonly equipe = inject(EquipeService);

  protected readonly roles = ROLES;
  protected readonly formulaire = inject(FormBuilder).nonNullable.group({
    prenom: '',
    nom: '',
    email: '',
    role: '' as RoleCabinet | '',
  });

  protected readonly membres = signal<Membre[]>([]);
  protected readonly chargement = signal(true);
  protected readonly erreurs = signal<Partial<Record<Champ, string>>>({});
  protected readonly alerte = signal<string | null>(null);
  protected readonly succes = signal<string | null>(null);
  protected readonly lienDeTest = signal<string | null>(null);
  protected readonly enCours = signal(false);

  ngOnInit(): void {
    this.equipe.membres().subscribe({
      next: (membres) => {
        this.membres.set(membres);
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.alerte.set(MESSAGE_INDISPONIBLE);
      },
    });
  }

  protected libelleRole(role: RoleCabinet): string {
    return ROLES.find((r) => r.valeur === role)?.libelle ?? role;
  }

  protected inviter(): void {
    if (this.enCours()) {
      return;
    }
    const demande = this.formulaire.getRawValue();
    const erreurs = erreursInvitation(demande);
    this.erreurs.set(erreurs);
    this.succes.set(null);
    this.lienDeTest.set(null);
    if (Object.keys(erreurs).length > 0) {
      this.alerte.set('Certains champs sont à corriger');
      return;
    }
    this.alerte.set(null);
    this.enCours.set(true);
    this.equipe.inviter(demande).subscribe({
      next: ({ membre, jetonDeTest }) => {
        this.enCours.set(false);
        this.membres.update((liste) => [...liste, membre].sort((a, b) => a.nomAffiche.localeCompare(b.nomAffiche)));
        this.succes.set(`Invitation envoyée à ${membre.email}`);
        this.lienDeTest.set(jetonDeTest ? `/activer-compte?jeton=${jetonDeTest}` : null);
        this.formulaire.reset();
      },
      error: (err: HttpErrorResponse) => {
        this.enCours.set(false);
        this.erreurs.set(err.error?.erreurs ?? {});
        this.alerte.set(err.error?.message ?? MESSAGE_INDISPONIBLE);
      },
    });
  }
}

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Mêmes règles que le backend : vérifiées avant l'envoi pour répondre tout de suite. */
export function erreursInvitation(demande: DemandeInvitation): Partial<Record<Champ, string>> {
  const erreurs: Partial<Record<Champ, string>> = {};
  if (!demande.prenom.trim()) {
    erreurs.prenom = 'Ce champ est obligatoire';
  }
  if (!demande.nom.trim()) {
    erreurs.nom = 'Ce champ est obligatoire';
  }
  const email = demande.email.trim();
  if (!email) {
    erreurs.email = 'Ce champ est obligatoire';
  } else if (!EMAIL.test(email)) {
    erreurs.email = 'Adresse email invalide';
  }
  if (!demande.role) {
    erreurs.role = 'Choisissez un rôle';
  }
  return erreurs;
}
