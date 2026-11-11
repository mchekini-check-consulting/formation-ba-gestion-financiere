import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Module } from './module.service';
import { MESSAGE_PROCHAINEMENT, ModulesMenu } from './modules-menu';

const MODULES: Module[] = [
  { code: 'SYNCHRONISATION_BANCAIRE', nom: 'Synchronisation bancaire', description: null, statut: 'BIENTOT_DISPONIBLE' },
  { code: 'RAPPROCHEMENT_AUTOMATIQUE', nom: 'Rapprochement automatique', description: null, statut: 'BIENTOT_DISPONIBLE' },
  { code: 'CRM', nom: 'CRM', description: 'Relation client', statut: 'BIENTOT_DISPONIBLE' },
  { code: 'SUIVI_TEMPS', nom: 'Suivi des temps', description: null, statut: 'BIENTOT_DISPONIBLE' },
];

describe('US-18 Modules à venir dans le menu de gauche', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ModulesMenu],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function ouvrir() {
    const fixture = TestBed.createComponent(ModulesMenu);
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  function modules(page: HTMLElement) {
    return Array.from(page.querySelectorAll<HTMLButtonElement>('.module'));
  }

  it('CA1 / RG1 / RG3 : les 4 modules envoyés par le serveur, avec « Bientôt »', async () => {
    const { fixture, page } = await ouvrir();
    http.expectOne('/api/modules').flush(MODULES);
    await fixture.whenStable();

    expect(page.querySelector('.titre')?.textContent?.trim()).toBe('Modules');
    expect(page.querySelector('.compteur')?.textContent).toBe('4');
    // Une icône par module ; un module inconnu de l'écran a l'icône par défaut (RG3).
    expect(page.querySelectorAll('.module .icone svg').length).toBe(4);
    expect(modules(page).map((m) => m.querySelector('.nom')?.textContent)).toEqual([
      'Synchronisation bancaire',
      'Rapprochement automatique',
      'CRM',
      'Suivi des temps',
    ]);
    expect(modules(page).map((m) => m.querySelector('.badge')?.textContent)).toEqual(Array(4).fill('Bientôt'));
    expect(page.querySelector('[role=status]')).toBeNull();
  });

  it('CA2 / RG2 : clic sur CRM -> « Ce module sera disponible prochainement », second clic le referme', async () => {
    const { fixture, page } = await ouvrir();
    http.expectOne('/api/modules').flush(MODULES);
    await fixture.whenStable();

    modules(page)[2].click();
    await fixture.whenStable();
    expect(page.querySelector('[role=status]')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      `CRM : ${MESSAGE_PROCHAINEMENT}`,
    );
    expect(modules(page)[2].getAttribute('aria-pressed')).toBe('true');

    modules(page)[2].click();
    await fixture.whenStable();
    expect(page.querySelector('[role=status]')).toBeNull();
  });

  it('RG3 : statut inconnu de l’écran affiché tel quel', async () => {
    const { fixture, page } = await ouvrir();
    http.expectOne('/api/modules').flush([{ ...MODULES[0], code: 'NOUVEAU', statut: 'EN_TEST' }]);
    await fixture.whenStable();
    expect(page.querySelector('.badge')?.textContent).toBe('EN_TEST');
    expect(page.querySelector('.module .icone svg')).not.toBeNull();
  });

  it('service indisponible : pas de rubrique Modules', async () => {
    const { fixture, page } = await ouvrir();
    http.expectOne('/api/modules').flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(page.querySelector('.titre')).toBeNull();
    expect(modules(page)).toEqual([]);
  });
});
