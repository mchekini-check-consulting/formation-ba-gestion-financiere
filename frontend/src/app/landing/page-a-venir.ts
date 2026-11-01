import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Destination des liens de la landing dont l'écran n'est pas encore développé. */
@Component({
  imports: [RouterLink],
  selector: 'app-page-a-venir',
  styles: `
    :host {
      display: grid;
      place-content: center;
      gap: 16px;
      min-height: 100vh;
      padding: 24px;
      text-align: center;
      background: #f6f2ea;
      color: #16130f;
      font-family: Inter, system-ui, sans-serif;
    }

    h1 {
      margin: 0;
      font-family: 'Playfair Display', Inter, sans-serif;
      font-size: 32px;
    }

    a {
      color: #8c6b36;
      font-weight: 600;
    }
  `,
  template: `
    <h1>Cette page arrive bientôt.</h1>
    <a routerLink="/">← Retour à l'accueil</a>
  `,
})
export class PageAVenir {}
