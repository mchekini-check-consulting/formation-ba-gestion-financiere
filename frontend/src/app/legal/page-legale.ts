import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { PAGES_LEGALES, PageLegaleId } from './contenus-legaux';

/**
 * US-34 (PTO-59) : mentions légales, CGU et politique de confidentialité, ouvertes depuis
 * le pied de page. Pages publiques, sans connexion (CA2) ; la page affichée vient de
 * `data.page` de la route. Le logo Pointéo ramène à la landing page.
 */
@Component({
  imports: [RouterLink],
  selector: 'app-page-legale',
  templateUrl: './page-legale.html',
  styleUrl: './page-legale.scss',
})
export class PageLegale {
  private readonly route = inject(ActivatedRoute);

  protected readonly onglets: { id: PageLegaleId; libelle: string }[] = [
    { id: 'mentions-legales', libelle: 'Mentions légales' },
    { id: 'cgu', libelle: 'CGU' },
    { id: 'confidentialite', libelle: 'Confidentialité' },
  ];

  protected readonly pageId = toSignal(this.route.data.pipe(map((d) => d['page'] as PageLegaleId)));
  protected readonly contenu = computed(() => {
    const page = this.pageId();
    return page ? PAGES_LEGALES[page] : undefined;
  });

  /** Sommaire : fait défiler jusqu'à la rubrique sans changer d'adresse. */
  protected allerA(event: Event, numero: number): void {
    event.preventDefault();
    document.getElementById(`rubrique-${numero}`)?.scrollIntoView({ behavior: 'smooth' });
  }
}
