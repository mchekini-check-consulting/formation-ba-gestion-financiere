import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../app.routes';
import { PAGES_LEGALES, VERSION_CGU } from './contenus-legaux';

describe('PageLegale (US-34)', () => {
  async function ouvrir(url: string): Promise<HTMLElement> {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return harness.routeNativeElement as HTMLElement;
  }

  function titresRubriques(page: HTMLElement): string[] {
    return [...page.querySelectorAll('.rubrique h2')].map((h) => h.textContent!.trim());
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter(routes)] });
  });

  it('CA1 : les CGU s’affichent avec leur version et leur date', async () => {
    const page = await ouvrir('/cgu');

    expect(page.querySelector('h1')!.textContent).toContain("Conditions générales d'utilisation");
    const sousTitre = page.querySelector('.sous-titre')!.textContent!;
    expect(sousTitre).toContain(`Version ${VERSION_CGU.version}`);
    expect(sousTitre).toContain(VERSION_CGU.date);
    expect(titresRubriques(page)).toEqual(PAGES_LEGALES.cgu.rubriques.map((r) => r.titre));
  });

  it('RG2 : les mentions légales donnent l’éditeur, l’hébergeur (UE) et un contact', async () => {
    const page = await ouvrir('/mentions-legales');

    expect(page.querySelector('h1')!.textContent).toContain('Mentions légales');
    expect(titresRubriques(page)).toEqual(
      expect.arrayContaining(['Éditeur du site', 'Hébergement', 'Contact']),
    );
    expect(page.textContent).toContain("hébergées dans l'Union européenne");
  });

  it('affiche la politique de confidentialité', async () => {
    const page = await ouvrir('/confidentialite');

    expect(page.querySelector('h1')!.textContent).toContain('Politique de confidentialité');
    expect(titresRubriques(page)).toContain('Vos droits');
  });

  it('le logo Pointéo et le lien « Retour » ramènent à la landing page', async () => {
    const page = await ouvrir('/cgu');

    expect(page.querySelector('.marque')!.getAttribute('href')).toBe('/');
    expect(page.querySelector('.marque')!.textContent).toContain('Pointéo');
    expect(page.querySelector('.retour')!.getAttribute('href')).toBe('/');
  });

  it('les onglets mènent aux 3 pages et signalent la page ouverte', async () => {
    const page = await ouvrir('/mentions-legales');

    const onglets = [...page.querySelectorAll<HTMLAnchorElement>('.onglet')];
    expect(onglets.map((o) => o.getAttribute('href'))).toEqual(['/mentions-legales', '/cgu', '/confidentialite']);
    const actif = page.querySelector('.onglet--actif')!;
    expect(actif.textContent!.trim()).toBe('Mentions légales');
    expect(actif.getAttribute('aria-current')).toBe('page');
  });

  it('le sommaire fait défiler jusqu’à la rubrique choisie', async () => {
    const page = await ouvrir('/cgu');
    const defiler = vi.fn();
    Element.prototype.scrollIntoView = defiler;

    page.querySelectorAll<HTMLAnchorElement>('.sommaire a')[1].click();

    expect(defiler).toHaveBeenCalledOnce();
    expect(defiler.mock.contexts[0]).toBe(page.querySelector('#rubrique-2'));
  });

  it('CA2 : les pages légales sont publiques (aucune vérification de session)', () => {
    for (const chemin of ['mentions-legales', 'cgu', 'confidentialite']) {
      const route = routes.find((r) => r.path === chemin)!;
      expect(route.canActivate).toBeUndefined();
      expect(route.canActivateChild).toBeUndefined();
    }
  });
});
