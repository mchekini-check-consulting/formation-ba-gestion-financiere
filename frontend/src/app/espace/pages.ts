import { Component, computed, inject } from '@angular/core';
import { SessionService } from '../session/session.service';

/**
 * Page d'arrivée du dirigeant après la connexion (US-03 RG3). Côté cabinet : liste des dossiers
 * (dossiers/liste-dossiers.ts, US-09) et dossier ouvert (dossiers/dossier.ts, US-11).
 */
@Component({
  selector: 'app-espace-client',
  styles: `
    h1 { margin: 0; font-family: 'Playfair Display', Inter, sans-serif; font-size: 30px; font-weight: 800; letter-spacing: -0.02em; }
    .sous-titre { margin: 4px 0 24px; font-size: 14px; color: #6e6456; }
  `,
  template: `
    <h1>Bonjour {{ nom() }}</h1>
    <p class="sous-titre">Votre espace client arrive avec une prochaine US.</p>
  `,
})
export class EspaceClient {
  private readonly session = inject(SessionService);
  protected readonly nom = computed(() => this.session.utilisateur()?.nomAffiche ?? '');
}
