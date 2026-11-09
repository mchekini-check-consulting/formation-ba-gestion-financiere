import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DossierClient, DossierService, EtatEspaceClient, LIBELLES_ETAT, formaterSiren } from './dossier.service';

/** US-11 RG2 : onglets du dossier, « Factures fournisseurs » ouvert par défaut. */
export type Onglet = 'fournisseurs' | 'clients' | 'modules';

export const ONGLETS: { id: Onglet; libelle: string }[] = [
  { id: 'fournisseurs', libelle: 'Factures fournisseurs' },
  { id: 'clients', libelle: 'Factures clients' },
  { id: 'modules', libelle: 'Modules' },
];

/** US-11 RG3 : dossier inexistant, d'un autre cabinet ou adresse tapée à la main. */
export const MESSAGE_INTROUVABLE = 'Dossier introuvable ou accès non autorisé';
const MESSAGE_INDISPONIBLE = 'Le service est momentanément indisponible. Réessayez.';

/** US-11 Ouvrir un dossier (US-10 RG3 : le dossier créé s'ouvre ici). */
@Component({
  imports: [RouterLink],
  selector: 'app-dossier',
  templateUrl: './dossier.html',
  styleUrl: './dossier.scss',
})
export class Dossier implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly dossiers = inject(DossierService);
  protected readonly dossier = signal<DossierClient | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly onglet = signal<Onglet>('fournisseurs');
  protected readonly onglets = ONGLETS;

  ngOnInit(): void {
    this.route.paramMap.subscribe((parametres) => {
      this.dossier.set(null);
      this.erreur.set(null);
      this.onglet.set('fournisseurs');
      // Le serveur vérifie les droits (RG3) : l'adresse est transmise telle quelle.
      this.dossiers.consulter(parametres.get('id') ?? '').subscribe({
        next: (dossier) => this.dossier.set(dossier),
        error: (err: HttpErrorResponse) =>
          this.erreur.set([400, 403, 404].includes(err.status) ? MESSAGE_INTROUVABLE : MESSAGE_INDISPONIBLE),
      });
    });
  }

  choisir(onglet: Onglet): void {
    this.onglet.set(onglet);
  }

  protected siren(dossier: DossierClient): string {
    return formaterSiren(dossier.siren);
  }

  protected libelleEtat(etat: EtatEspaceClient): string {
    return LIBELLES_ETAT[etat];
  }
}
