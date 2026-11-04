import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { SessionService } from '../session/session.service';

/**
 * Pages d'arrivée après la connexion (US-03 RG3). Leur contenu réel viendra avec les US
 * « Dossiers » et « Espace client » : dossiers d'exemple de la maquette, pour l'instant.
 */
export const DOSSIERS_EXEMPLE = [
  { id: 1, nom: 'Boulangerie Leroy SARL', siren: '812 345 678', initiales: 'BL' },
  { id: 2, nom: 'Garage Morel SAS', siren: '798 654 321', initiales: 'GM' },
];

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
`;

@Component({
  imports: [RouterLink],
  selector: 'app-dossiers',
  styles: STYLES,
  template: `
    <h1>Dossiers clients</h1>
    <p class="sous-titre">{{ nom() }}</p>
    <section class="carte">
      @for (dossier of dossiers; track dossier.id) {
        <a class="ligne" [routerLink]="['/dossiers', dossier.id]">
          <span class="pastille">{{ dossier.initiales }}</span>
          <span><strong>{{ dossier.nom }}</strong><span class="muted">SIREN {{ dossier.siren }}</span></span>
        </a>
      }
    </section>
    <p class="note">Dossiers d'exemple : la liste réelle arrive avec l'US « Liste des dossiers ».</p>
  `,
})
export class Dossiers {
  protected readonly dossiers = DOSSIERS_EXEMPLE;
  private readonly session = inject(SessionService);
  protected readonly nom = computed(() => this.session.utilisateur()?.nomAffiche ?? '');
}

@Component({
  imports: [RouterLink],
  selector: 'app-dossier',
  styles: STYLES,
  template: `
    <a routerLink="/dossiers" class="retour">← Dossiers</a>
    <h1>{{ dossier()?.nom ?? 'Dossier introuvable' }}</h1>
    <p class="sous-titre">Le détail du dossier arrive avec une prochaine US.</p>
  `,
})
export class Dossier {
  private readonly id = toSignal(inject(ActivatedRoute).paramMap.pipe(map((p) => Number(p.get('id')))));
  protected readonly dossier = computed(() => DOSSIERS_EXEMPLE.find((d) => d.id === this.id()));
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
