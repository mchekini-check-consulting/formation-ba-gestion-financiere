import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DossierClient, DossierService, formaterSiren } from '../dossiers/dossier.service';
import { SessionService } from '../session/session.service';

/**
 * Pages d'arrivée après la connexion (US-03 RG3) : un dossier client (US-10/US-11) et l'espace
 * client du dirigeant. La liste des dossiers est dans dossiers/liste-dossiers.ts (US-09).
 */
const MESSAGE_INDISPONIBLE = 'Le service est momentanément indisponible. Réessayez.';

const STYLES = `
  h1 { margin: 0; font-family: 'Playfair Display', Inter, sans-serif; font-size: 30px; font-weight: 800; letter-spacing: -0.02em; }
  .sous-titre { margin: 4px 0 24px; font-size: 14px; color: #6e6456; }
  .carte { background: #fffdf9; border: 1px solid #e7dfd0; border-radius: 16px; overflow: hidden; }
  .ligne { display: flex; align-items: center; gap: 12px; padding: 16px 18px; border-top: 1px solid #f1ebe0; color: #16130f; text-decoration: none; }
  .ligne:first-child { border-top: 0; }
  .ligne:hover { background: #fbf8f2; }
  .pastille { display: flex; align-items: center; justify-content: center; width: 38px; height: 38px; border-radius: 10px; background: #f1ebe0; color: #3a332a; font-size: 13px; font-weight: 700; }
  .ligne span:last-child { display: flex; flex-direction: column; }
  .muted { font-size: 13px; color: #6e6456; }
  .note { margin-top: 16px; font-size: 13px; color: #6e6456; }
  a.retour { color: #8c6b36; font-weight: 600; text-decoration: none; }
  .entete { display: flex; flex-wrap: wrap; align-items: flex-start; justify-content: space-between; gap: 12px; }
  .bouton { padding: 12px 20px; border-radius: 10px; background: #8c6b36; color: #fffdf9; font-weight: 600; text-decoration: none; }
  .alerte { padding: 12px 14px; border-radius: 10px; background: #fdecea; color: #8c1d18; font-size: 14px; }
  dl { display: grid; grid-template-columns: max-content 1fr; gap: 10px 24px; margin: 0; padding: 18px; }
  dt { color: #6e6456; font-size: 14px; }
  dd { margin: 0; font-weight: 600; }
`;

@Component({
  imports: [RouterLink],
  selector: 'app-dossier',
  styles: STYLES,
  template: `
    <a routerLink="/dossiers" class="retour">← Dossiers</a>
    @if (dossier(); as d) {
      <h1>{{ d.raisonSociale }}</h1>
      <p class="sous-titre">Dossier client</p>
      <section class="carte">
        <dl>
          <dt>Raison sociale</dt><dd>{{ d.raisonSociale }}</dd>
          <dt>SIREN</dt><dd>{{ siren(d) }}</dd>
        </dl>
      </section>
      <p class="note">Les factures du client arriveront ici avec une prochaine US.</p>
    } @else if (erreur(); as e) {
      <h1>Dossier introuvable</h1>
      <p class="alerte" role="alert">{{ e }}</p>
    } @else {
      <p class="muted">Chargement…</p>
    }
  `,
})
export class Dossier implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly dossiers = inject(DossierService);
  protected readonly dossier = signal<DossierClient | null>(null);
  protected readonly erreur = signal<string | null>(null);

  /** US-10 RG3 : le dossier qui vient d'être créé s'ouvre ici. */
  ngOnInit(): void {
    this.route.paramMap.subscribe((parametres) => {
      this.dossier.set(null);
      this.erreur.set(null);
      this.dossiers.consulter(Number(parametres.get('id'))).subscribe({
        next: (dossier) => this.dossier.set(dossier),
        error: (err: HttpErrorResponse) =>
          this.erreur.set(err.status === 404 ? "Ce dossier n'existe pas dans votre cabinet." : MESSAGE_INDISPONIBLE),
      });
    });
  }

  protected siren(dossier: DossierClient): string {
    return formaterSiren(dossier.siren);
  }
}

@Component({
  selector: 'app-espace-client',
  styles: STYLES,
  template: `
    <h1>Bonjour {{ nom() }}</h1>
    <p class="sous-titre">Votre espace client arrive avec une prochaine US.</p>
  `,
})
export class EspaceClient {
  private readonly session = inject(SessionService);
  protected readonly nom = computed(() => this.session.utilisateur()?.nomAffiche ?? '');
}
