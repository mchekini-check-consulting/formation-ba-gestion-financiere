import { Component, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Apercu } from './apercu/apercu';
import { ETAPES, FAQ, MODULES, TUILES_CABINETS, TUILES_ENTREPRISES } from './contenu';
import { SectionTarifs } from './section-tarifs/section-tarifs';

export type Profil = 'cabinets' | 'entreprises';

// Page d'accueil publique de Pointéo (maquette « Landing page »)
@Component({
  selector: 'app-landing',
  imports: [Apercu, RouterLink, SectionTarifs],
  styleUrl: './landing.scss',
  templateUrl: './landing.html',
})
export class Landing {
  protected readonly etapes = ETAPES;
  protected readonly modules = MODULES;
  protected readonly faq = FAQ;

  /** Onglet « Pensé pour vous » affiché */
  protected readonly profil = signal<Profil>('cabinets');
  protected readonly tuiles = computed(() =>
    this.profil() === 'cabinets' ? TUILES_CABINETS : TUILES_ENTREPRISES,
  );

  /** Question de la FAQ dépliée (-1 = aucune) */
  protected readonly questionOuverte = signal(0);

  protected readonly bandeauCookies = signal(true);

  protected choisirProfil(profil: Profil): void {
    this.profil.set(profil);
  }

  protected basculerQuestion(index: number): void {
    this.questionOuverte.update((ouverte) => (ouverte === index ? -1 : index));
  }

  protected fermerCookies(): void {
    this.bandeauCookies.set(false);
  }
}
