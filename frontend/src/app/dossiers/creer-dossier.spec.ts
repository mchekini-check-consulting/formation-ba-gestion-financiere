import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { CreerDossier, erreursDossier } from './creer-dossier';
import { formaterSiren } from './dossier.service';

const DOUBLON = 'Un dossier existe déjà pour ce SIREN dans ce cabinet';
const OBLIGATOIRE = 'Ce champ est obligatoire';

describe('Créer un dossier client (US-10)', () => {
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CreerDossier],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  async function ouvrir() {
    const fixture = TestBed.createComponent(CreerDossier);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const stable = () => fixture.whenStable();
    const saisir = async (selecteur: string, valeur: string) => {
      const champ = page.querySelector<HTMLInputElement>(selecteur)!;
      champ.value = valeur;
      champ.dispatchEvent(new Event('input'));
      await stable();
    };
    const creer = async () => {
      page.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
      await stable();
    };
    const texte = (selecteur: string) => page.querySelector(selecteur)?.textContent?.trim();
    return { page, stable, saisir, creer, texte };
  }

  it('CA1 : le dossier de Fleurs Martin EURL est créé puis s’ouvre (RG3)', async () => {
    const { saisir, creer, stable, page } = await ouvrir();
    await saisir('#raisonSociale', 'Fleurs Martin EURL');
    await saisir('#siren', '812 345 679');
    await creer();

    const requete = http.expectOne('/api/dossiers');
    expect(requete.request.method).toBe('POST');
    expect(requete.request.body).toEqual({ raisonSociale: 'Fleurs Martin EURL', siren: '812 345 679' });
    expect(page.querySelector('button[type=submit]')?.textContent?.trim()).toBe('Création…');
    requete.flush({ id: 42, raisonSociale: 'Fleurs Martin EURL', siren: '812345679', creeLe: '2026-11-08T09:00:00Z' });
    await stable();
    expect(navigate).toHaveBeenCalledWith(['/dossiers', 42]);
  });

  it('CA2 : SIREN déjà suivi par le cabinet', async () => {
    const { saisir, creer, stable, texte } = await ouvrir();
    await saisir('#raisonSociale', 'Boulangerie Leroy');
    await saisir('#siren', '812345678');
    await creer();
    http
      .expectOne('/api/dossiers')
      .flush({ message: DOUBLON, erreurs: { siren: DOUBLON } }, { status: 409, statusText: 'Conflict' });
    await stable();
    expect(texte('[role=alert]')).toBe(DOUBLON);
    expect(texte('.erreur')).toBe(DOUBLON);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('RG1 : champs obligatoires vérifiés avant l’envoi', async () => {
    const { creer, texte, page } = await ouvrir();
    await creer();
    http.expectNone('/api/dossiers');
    expect(texte('[role=alert]')).toBe('Certains champs sont à corriger');
    expect([...page.querySelectorAll('.erreur')].map((e) => e.textContent)).toEqual([OBLIGATOIRE, OBLIGATOIRE]);
  });

  it('service indisponible', async () => {
    const { saisir, creer, stable, texte } = await ouvrir();
    await saisir('#raisonSociale', 'Fleurs Martin EURL');
    await saisir('#siren', '812345679');
    await creer();
    http.expectOne('/api/dossiers').flush(null, { status: 503, statusText: 'Unavailable' });
    await stable();
    expect(texte('[role=alert]')).toBe('Le service est momentanément indisponible. Réessayez.');
  });

  it('règles du formulaire', () => {
    expect(erreursDossier({ raisonSociale: 'Fleurs Martin EURL', siren: '812.345-679' })).toEqual({});
    expect(erreursDossier({ raisonSociale: 'x'.repeat(201), siren: '8123' })).toEqual({
      raisonSociale: 'Ce champ est trop long',
      siren: 'Le SIREN comporte 9 chiffres',
    });
    expect(formaterSiren('812345679')).toBe('812 345 679');
  });
});
