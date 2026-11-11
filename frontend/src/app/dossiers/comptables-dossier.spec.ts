import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionService } from '../session/session.service';
import { ComptablesDossier, ComptablesDossierComponent, nomsComptables } from './comptables-dossier';

const URL = '/api/dossiers/42/comptables';
const SANS_COMPTABLE: ComptablesDossier = {
  comptables: [
    { id: 5, nomAffiche: 'Lucie Bernard', attribue: false },
    { id: 6, nomAffiche: 'Marc Petit', attribue: false },
  ],
  modifiable: true,
};

describe('US-20 Comptables du dossier', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [ComptablesDossierComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).ouvrir({ jeton: 'jeton', role: 'COLLABORATEUR', nomAffiche: 'Jean Dupont' });
  });

  afterEach(() => http.verify());

  async function afficher(vue: ComptablesDossier) {
    const fixture: ComponentFixture<ComptablesDossierComponent> = TestBed.createComponent(ComptablesDossierComponent);
    fixture.componentRef.setInput('dossierId', 42);
    await fixture.whenStable();
    http.expectOne(URL).flush(vue);
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  function bouton(page: HTMLElement, texte: string): HTMLButtonElement {
    return Array.from(page.querySelectorAll<HTMLButtonElement>('button')).find(
      (b) => b.textContent?.trim() === texte,
    ) as HTMLButtonElement;
  }

  it('noms affichés', () => {
    expect(nomsComptables([])).toBe('Aucun comptable');
    expect(nomsComptables(['Lucie Bernard', 'Marc Petit'])).toBe('Lucie Bernard, Marc Petit');
  });

  it('CA1 : Jean attribue Garage Morel SAS à Lucie', async () => {
    const { fixture, page } = await afficher(SANS_COMPTABLE);
    expect(page.querySelector('.comptables')?.textContent).toBe('Comptables : Aucun comptable');

    bouton(page, 'Attribuer').click();
    await fixture.whenStable();
    const cases = page.querySelectorAll<HTMLInputElement>('input[type=checkbox]');
    expect(cases).toHaveLength(2);
    cases[0].click();
    cases[1].click();
    cases[1].click();
    await fixture.whenStable();
    bouton(page, 'Enregistrer').click();

    const envoi = http.expectOne(URL);
    expect(envoi.request.method).toBe('PUT');
    expect(envoi.request.body).toEqual({ comptableIds: [5] });
    envoi.flush({
      ...SANS_COMPTABLE,
      comptables: [{ ...SANS_COMPTABLE.comptables[0], attribue: true }, SANS_COMPTABLE.comptables[1]],
    });
    await fixture.whenStable();
    expect(page.querySelector('.comptables')?.textContent).toBe('Comptables : Lucie Bernard');
    expect(page.querySelector('fieldset')).toBeNull();
  });

  it('RG1 : un comptable voit la liste sans « Attribuer »', async () => {
    const { page } = await afficher({
      comptables: [{ id: 5, nomAffiche: 'Lucie Bernard', attribue: true }],
      modifiable: false,
    });
    expect(page.querySelector('.comptables')?.textContent).toBe('Comptables : Lucie Bernard');
    expect(bouton(page, 'Attribuer')).toBeUndefined();
  });

  it('« Annuler » ferme sans rien envoyer ; cabinet sans comptable', async () => {
    const { fixture, page } = await afficher({ comptables: [], modifiable: true });
    bouton(page, 'Attribuer').click();
    await fixture.whenStable();
    expect(page.textContent).toContain('Aucun comptable dans le cabinet');
    bouton(page, 'Annuler').click();
    await fixture.whenStable();
    expect(page.querySelector('fieldset')).toBeNull();
  });

  it('refus du serveur ou service indisponible', async () => {
    const { fixture, page } = await afficher(SANS_COMPTABLE);
    bouton(page, 'Attribuer').click();
    await fixture.whenStable();
    bouton(page, 'Enregistrer').click();
    const message = "Seuls l'administrateur et les associés attribuent les dossiers";
    http.expectOne(URL).flush({ message }, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toBe(message);

    bouton(page, 'Enregistrer').click();
    http.expectOne(URL).flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('momentanément indisponible');
  });

  it('chargement impossible', async () => {
    const fixture = TestBed.createComponent(ComptablesDossierComponent);
    fixture.componentRef.setInput('dossierId', 42);
    await fixture.whenStable();
    http.expectOne(URL).flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role=alert]')).not.toBeNull();
  });
});
