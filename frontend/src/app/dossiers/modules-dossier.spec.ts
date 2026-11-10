import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ModuleDossier } from './module.service';
import { MESSAGE_PROCHAINEMENT, ModulesDossier } from './modules-dossier';

const MODULES: ModuleDossier[] = [
  { code: 'CRM', nom: 'CRM', description: 'Suivez la relation avec le client du dossier.', statut: 'BIENTOT_DISPONIBLE' },
  { code: 'SUIVI_TEMPS', nom: 'Suivi des temps', description: null, statut: 'BIENTOT_DISPONIBLE' },
];

describe('US-18 Modules à venir dans un dossier', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ModulesDossier],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function ouvrir() {
    const fixture = TestBed.createComponent(ModulesDossier);
    fixture.componentRef.setInput('dossierId', 2);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Chargement…');
    return { fixture, page };
  }

  function tuiles(page: HTMLElement) {
    return Array.from(page.querySelectorAll<HTMLButtonElement>('.tuile'));
  }

  it('CA1 / RG1 / RG3 : tuiles envoyées par le serveur, avec « Bientôt disponible »', async () => {
    const { fixture, page } = await ouvrir();
    http.expectOne('/api/dossiers/2/modules').flush(MODULES);
    await fixture.whenStable();

    expect(tuiles(page).map((t) => t.querySelector('.nom')?.textContent)).toEqual(['CRM', 'Suivi des temps']);
    expect(tuiles(page).map((t) => t.querySelector('.badge')?.textContent)).toEqual([
      'Bientôt disponible',
      'Bientôt disponible',
    ]);
    expect(tuiles(page)[0].textContent).toContain('Suivez la relation');
    expect(tuiles(page)[1].querySelector('.description')).toBeNull();
    expect(page.querySelector('[role=status]')).toBeNull();
  });

  it('CA2 / RG2 : clic sur CRM -> « Ce module sera disponible prochainement »', async () => {
    const { fixture, page } = await ouvrir();
    http.expectOne('/api/dossiers/2/modules').flush(MODULES);
    await fixture.whenStable();

    tuiles(page)[0].click();
    await fixture.whenStable();
    expect(page.querySelector('[role=status]')?.textContent).toBe(`CRM : ${MESSAGE_PROCHAINEMENT}`);
    expect(tuiles(page)[0].getAttribute('aria-pressed')).toBe('true');
  });

  it('service indisponible', async () => {
    const { fixture, page } = await ouvrir();
    http.expectOne('/api/dossiers/2/modules').flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('momentanément indisponible');
  });
});
