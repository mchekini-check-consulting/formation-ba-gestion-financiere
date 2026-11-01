import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Cabinet, CabinetRequest, CabinetsService } from '../cabinets';

const VIDE: CabinetRequest = { nom: '', siren: '', email: '', nombreDossiers: 0 };

// Message compréhensible pour chaque erreur renvoyée par le backend « cabinets »
export function messageErreur(action: string, e: HttpErrorResponse): string {
  switch (e.status) {
    case 400:
      return `${action} impossible : vérifiez le nom, le SIREN (9 chiffres) et l'email.`;
    case 404:
      return `${action} impossible : ce cabinet n'existe plus.`;
    case 409:
      return `${action} impossible : un cabinet existe déjà avec ce SIREN.`;
    default:
      return `${action} impossible : service indisponible (${e.status}).`;
  }
}

// Écran « Créer un compte » : création, liste, modification et suppression des cabinets
@Component({
  selector: 'app-cabinets-page',
  imports: [RouterLink],
  styleUrl: './cabinets-page.scss',
  templateUrl: './cabinets-page.html',
})
export class CabinetsPage implements OnInit {
  private readonly api = inject(CabinetsService);

  /** Nombre de dossiers transmis par le simulateur de tarif (?dossiers=...) */
  readonly dossiers = input<string>();

  protected readonly cabinets = signal<Cabinet[]>([]);
  protected readonly formulaire = signal<CabinetRequest>({ ...VIDE });
  /** Cabinet en cours de modification (null = création) */
  protected readonly enModification = signal<number | null>(null);
  protected readonly erreur = signal('');
  protected readonly confirmation = signal('');

  ngOnInit(): void {
    const dossiers = Number(this.dossiers());
    if (Number.isInteger(dossiers) && dossiers > 0) {
      this.formulaire.update((f) => ({ ...f, nombreDossiers: dossiers }));
    }
    this.api.lister().subscribe({
      next: (cabinets) => this.cabinets.set(cabinets),
      error: (e: HttpErrorResponse) => this.erreur.set(messageErreur('Chargement', e)),
    });
  }

  protected saisir(champ: keyof CabinetRequest, valeur: string): void {
    this.formulaire.update((f) => ({
      ...f,
      [champ]: champ === 'nombreDossiers' ? Number(valeur) : valeur,
    }));
  }

  protected enregistrer(event: Event): void {
    event.preventDefault();
    this.erreur.set('');
    this.confirmation.set('');
    const id = this.enModification();
    const cabinet = this.formulaire();
    if (id === null) {
      this.api.creer(cabinet).subscribe({
        next: (cree) => {
          this.cabinets.update((liste) => [...liste, cree]);
          this.terminer(`Le compte du cabinet « ${cree.nom} » est créé.`);
        },
        error: (e: HttpErrorResponse) => this.erreur.set(messageErreur('Création', e)),
      });
    } else {
      this.api.modifier(id, cabinet).subscribe({
        next: (modifie) => {
          this.cabinets.update((liste) => liste.map((c) => (c.id === id ? modifie : c)));
          this.terminer(`Le cabinet « ${modifie.nom} » est modifié.`);
        },
        error: (e: HttpErrorResponse) => this.erreur.set(messageErreur('Modification', e)),
      });
    }
  }

  protected modifier(cabinet: Cabinet): void {
    this.erreur.set('');
    this.confirmation.set('');
    this.enModification.set(cabinet.id);
    this.formulaire.set({
      nom: cabinet.nom,
      siren: cabinet.siren,
      email: cabinet.email,
      nombreDossiers: cabinet.nombreDossiers,
    });
  }

  protected annuler(): void {
    this.enModification.set(null);
    this.formulaire.set({ ...VIDE });
  }

  protected supprimer(cabinet: Cabinet): void {
    if (!confirm(`Supprimer définitivement le cabinet « ${cabinet.nom} » ?`)) {
      return;
    }
    this.erreur.set('');
    this.confirmation.set('');
    this.api.supprimer(cabinet.id).subscribe({
      next: () => {
        this.cabinets.update((liste) => liste.filter((c) => c.id !== cabinet.id));
        if (this.enModification() === cabinet.id) {
          this.annuler();
        }
        this.confirmation.set(`Le cabinet « ${cabinet.nom} » est supprimé.`);
      },
      error: (e: HttpErrorResponse) => this.erreur.set(messageErreur('Suppression', e)),
    });
  }

  private terminer(message: string): void {
    this.annuler();
    this.confirmation.set(message);
  }
}
