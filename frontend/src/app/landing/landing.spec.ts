import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Landing } from './landing';

describe('Landing', () => {
  let fixture: ComponentFixture<Landing>;
  let page: HTMLElement;

  const texte = (selecteur: string) =>
    page.querySelector(selecteur)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const cliquer = async (element: Element | null) => {
    (element as HTMLElement).click();
    await fixture.whenStable();
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    fixture = TestBed.createComponent(Landing);
    page = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  it("affiche l'accroche et toutes les sections de la maquette", () => {
    expect(texte('h1')).toBe('De la transaction au bilan, tout se fait tout seul.');
    for (const id of ['etapes', 'fonctionnalites', 'modules', 'securite', 'tarifs', 'faq']) {
      expect(page.querySelector(`#${id}`)).not.toBeNull();
    }
    expect(page.querySelectorAll('#etapes li').length).toBe(3);
    expect(page.querySelector('app-section-tarifs #tarifs')).not.toBeNull();
  });

  it('bascule entre les fonctionnalités cabinets et entreprises', async () => {
    const [cabinets, entreprises] = Array.from(page.querySelectorAll('[role=tab]'));
    expect(cabinets.getAttribute('aria-selected')).toBe('true');
    expect(page.querySelectorAll('.tuile').length).toBe(6);

    await cliquer(entreprises);
    expect(entreprises.getAttribute('aria-selected')).toBe('true');
    expect(cabinets.getAttribute('aria-selected')).toBe('false');
    expect(page.querySelectorAll('.tuile').length).toBe(4);
    expect(texte('.tuile h3')).toBe('Déposer factures et notes de frais');

    await cliquer(page.querySelector('nav a'));
    expect(page.querySelectorAll('.tuile').length).toBe(6);
  });

  it('affiche « Bientôt disponible » seulement sur les fonctionnalités à venir', () => {
    const derniere = page.querySelectorAll('.tuile')[5];
    expect(derniere.textContent).toContain('Dossiers clients et équipe');
    expect(derniere.querySelector('.etiquette--bientot')).toBeNull();
    expect(page.querySelector('.tuile .etiquette--bientot')).not.toBeNull();
  });

  it('déplie une seule question de la FAQ à la fois', async () => {
    const questions = () =>
      Array.from(page.querySelectorAll<HTMLButtonElement>('.faq__item button'));
    expect(questions().length).toBe(7);
    expect(page.querySelectorAll('.faq__item p').length).toBe(1);
    expect(questions()[0].getAttribute('aria-expanded')).toBe('true');

    await cliquer(questions()[2]);
    expect(questions()[0].getAttribute('aria-expanded')).toBe('false');
    expect(texte('.faq__item p')).toContain('10 Mo par fichier');

    await cliquer(questions()[2]);
    expect(page.querySelectorAll('.faq__item p').length).toBe(0);
  });

  it('ferme le bandeau cookies', async () => {
    expect(page.querySelector('[aria-label="Gestion des cookies"]')).not.toBeNull();
    const boutons = page.querySelectorAll('.cookies__boutons button');
    await cliquer(boutons[2]);
    expect(page.querySelector('[aria-label="Gestion des cookies"]')).toBeNull();
  });

  it('mène les boutons « Créer un compte » vers l’écran des cabinets', () => {
    const liens = Array.from(page.querySelectorAll('a')).filter((a) =>
      a.textContent?.includes('Créer'),
    );
    // 4 boutons de la page + celui du simulateur (qui ajoute ?dossiers=...)
    expect(liens.length).toBe(5);
    for (const lien of liens) {
      expect(lien.getAttribute('href')).toMatch(/^\/cabinets/);
    }
  });
});
