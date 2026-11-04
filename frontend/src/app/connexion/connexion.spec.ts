import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Connexion } from './connexion';

describe('Connexion (US-03)', () => {
  let http: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Connexion],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => http.verify());

  async function ouvrir() {
    const fixture = TestBed.createComponent(Connexion);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const saisir = (selecteur: string, valeur: string) => {
      const input = page.querySelector<HTMLInputElement>(selecteur)!;
      input.value = valeur;
      input.dispatchEvent(new Event('input'));
    };
    const envoyer = async () => {
      page.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
      await fixture.whenStable();
    };
    return { fixture, page, saisir, envoyer };
  }

  it('affiche le formulaire de la maquette', async () => {
    const { page } = await ouvrir();
    expect(page.querySelector('h1')?.textContent).toContain('Se connecter');
    expect(page.querySelector('label[for=email]')?.textContent).toContain('Email');
    expect(page.querySelector('label[for=mot-de-passe]')?.textContent).toContain('Mot de passe');
    expect(page.querySelector('[role=alert]')).toBeNull();
  });

  it('CA1 : bons identifiants -> envoie la demande puis ouvre l\'écran du code SMS', async () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const { saisir, envoyer } = await ouvrir();
    saisir('#email', 'jean.dupont@cabinet-test.fr');
    saisir('#mot-de-passe', 'Pointeo2026!');
    await envoyer();

    const requete = http.expectOne('/api/auth/connexion');
    expect(requete.request.method).toBe('POST');
    expect(requete.request.body).toEqual({ email: 'jean.dupont@cabinet-test.fr', motDePasse: 'Pointeo2026!' });
    requete.flush({ role: 'COLLABORATEUR', etapeSuivante: 'CODE_SMS' });

    expect(navigate).toHaveBeenCalledWith(['/verification-code'], { state: { role: 'COLLABORATEUR' } });
  });

  it('CA2 : mauvais mot de passe -> « Email ou mot de passe incorrect »', async () => {
    const { fixture, page, saisir, envoyer } = await ouvrir();
    saisir('#email', 'jean.dupont@cabinet-test.fr');
    saisir('#mot-de-passe', 'mauvais');
    await envoyer();

    http
      .expectOne('/api/auth/connexion')
      .flush({ message: 'Email ou mot de passe incorrect' }, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    expect(page.querySelector('[role=alert]')?.textContent).toContain('Email ou mot de passe incorrect');
    expect(page.querySelector<HTMLInputElement>('#mot-de-passe')!.value).toBe('');
    expect(page.querySelector<HTMLInputElement>('#email')!.value).toBe('jean.dupont@cabinet-test.fr');
    expect(page.textContent).not.toContain('Ce champ est obligatoire');
  });

  it('champs vides -> « Ce champ est obligatoire » sans appeler le backend', async () => {
    const { page, envoyer } = await ouvrir();
    expect(page.textContent).not.toContain('Ce champ est obligatoire');
    await envoyer();
    expect(page.querySelectorAll('.field__error').length).toBe(2);
    expect(page.textContent).toContain('Ce champ est obligatoire');
    http.expectNone('/api/auth/connexion');
  });

  it('backend injoignable -> message de service indisponible', async () => {
    const { fixture, page, saisir, envoyer } = await ouvrir();
    saisir('#email', 'jean.dupont@cabinet-test.fr');
    saisir('#mot-de-passe', 'Pointeo2026!');
    await envoyer();
    http.expectOne('/api/auth/connexion').flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('momentanément impossible');
  });

  it('le bouton œil affiche puis masque le mot de passe', async () => {
    const { fixture, page } = await ouvrir();
    const champ = () => page.querySelector<HTMLInputElement>('#mot-de-passe')!;
    const oeil = page.querySelector<HTMLButtonElement>('.password__toggle')!;
    expect(champ().type).toBe('password');
    oeil.click();
    await fixture.whenStable();
    expect(champ().type).toBe('text');
    expect(oeil.getAttribute('aria-label')).toBe('Masquer le mot de passe');
    oeil.click();
    await fixture.whenStable();
    expect(champ().type).toBe('password');
  });
});
