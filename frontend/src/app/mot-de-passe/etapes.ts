import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Panneau de gauche des écrans « Mot de passe oublié » (US-06). */
@Component({
  imports: [RouterLink],
  selector: 'app-etapes-mot-de-passe',
  styleUrl: './mot-de-passe.scss',
  templateUrl: './etapes.html',
  host: { style: 'display: contents' },
})
export class EtapesMotDePasse {
  readonly etape = input.required<1 | 2>();
}
