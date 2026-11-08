import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { Connexion } from './connexion';

describe('Connexion (US-03, US-05)', () => {
  let http: HttpTestingController;
  let router: Router;

  async function configurer(raison: string | null = null) {
    await TestBed.configureTestingModule({
      imports: [Connexion],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(raison ? { raison } : {}) } },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  }

  beforeEach(() => history.replaceState(null, ''));
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
    const seConnecter = async (motDePasse = 'Pointeo2026!') => {
      saisir('#email', 'jean.dupont@cabinet-test.fr');
      saisir('#mot-de-passe', motDePasse);
      await envoyer();
      return http.expectOne('/api/auth/connexion');
    };
    return { fixture, page, saisir, envoyer, seConnecter };
  }

  it('affiche le formulaire de la maquette', async () => {
    await configurer();
    const { page } = await ouvrir();
    expect(page.querySelector('h1')?.textContent).toContain('Se connecter');
    expect(page.querySelector('label[for=email]')?.textContent).toContain('Email');
    expect(page.querySelector('label[for=mot-de-passe]')?.textContent).toContain('Mot de passe');
    expect(page.querySelector('[role=alert]')).toBeNull();
  });

  it("US-03 CA1 : bons identifiants -> ouvre l'écran du code reçu par email avec le défi", async () => {
    await configurer();
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const { seConnecter } = await ouvrir();
    const requete = await seConnecter();
    expect(requete.request.method).toBe('POST');
    expect(requete.request.body).toEqual({ email: 'jean.dupont@cabinet-test.fr', motDePasse: 'Pointeo2026!' });
    requete.flush({ etapeSuivante: 'CODE_EMAIL', defi: 'd', emailMasque: 'j•••••@cabinet-test.fr', codeDeTest: null });

    expect(navigate).toHaveBeenCalledWith(['/verification-code'], {
      state: { defi: 'd', emailMasque: 'j•••••@cabinet-test.fr', codeDeTest: null },
    });
  });

  it('US-03 CA2 : mauvais mot de passe -> « Email ou mot de passe incorrect »', async () => {
    await configurer();
    const { fixture, page, seConnecter } = await ouvrir();
    (await seConnecter('mauvais')).flush(
      { message: 'Email ou mot de passe incorrect' },
      { status: 401, statusText: 'Unauthorized' },
    );
    await fixture.whenStable();

    expect(page.querySelector('[role=alert]')?.textContent).toContain('Email ou mot de passe incorrect');
    expect(page.querySelector<HTMLInputElement>('#mot-de-passe')!.value).toBe('');
    expect(page.querySelector<HTMLInputElement>('#email')!.value).toBe('jean.dupont@cabinet-test.fr');
    expect(page.textContent).not.toContain('Ce champ est obligatoire');
  });

  it('US-05 CA1 : compte bloqué -> message du serveur en avertissement', async () => {
    await configurer();
    const { fixture, page, seConnecter } = await ouvrir();
    (await seConnecter('mauvais')).flush(
      { message: 'Compte bloqué 15 minutes : trop de mots de passe incorrects.' },
      { status: 423, statusText: 'Locked' },
    );
    await fixture.whenStable();
    const alerte = page.querySelector('[role=alert]')!;
    expect(alerte.textContent).toContain('Compte bloqué 15 minutes');
    expect(alerte.classList).toContain('alert--avertissement');
  });

  it("US-04 : email impossible -> message du serveur", async () => {
    await configurer();
    const { fixture, page, seConnecter } = await ouvrir();
    (await seConnecter()).flush(
      { message: 'L’email n’a pas pu être envoyé. Réessayez.' },
      { status: 503, statusText: 'Unavailable' },
    );
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('L’email n’a pas pu être envoyé. Réessayez.');
  });

  it('backend injoignable -> message de service indisponible', async () => {
    await configurer();
    const { fixture, page, seConnecter } = await ouvrir();
    (await seConnecter()).flush(null, { status: 500, statusText: 'Erreur' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('momentanément impossible');
  });

  it('champs vides -> « Ce champ est obligatoire » sans appeler le backend', async () => {
    await configurer();
    const { page, envoyer } = await ouvrir();
    expect(page.textContent).not.toContain('Ce champ est obligatoire');
    await envoyer();
    expect(page.querySelectorAll('.field__error').length).toBe(2);
    http.expectNone('/api/auth/connexion');
  });

  it('le bouton œil affiche puis masque le mot de passe', async () => {
    await configurer();
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

  it.each([
    ['deconnexion', 'Vous êtes déconnecté'],
    ['session-expiree', 'Votre session a expiré, reconnectez-vous'],
    ['mot-de-passe-change', 'Votre mot de passe a été changé'],
  ])('US-07 / US-06 : ?raison=%s affiche « %s »', async (raison, texte) => {
    await configurer(raison);
    const { page } = await ouvrir();
    expect(page.querySelector('[data-testid=info-connexion]')?.textContent).toContain(texte);
  });

  it("US-04 RG3 : affiche le message transmis par l'écran du code", async () => {
    history.replaceState({ message: 'Trop de codes incorrects. Reconnectez-vous.' }, '');
    await configurer();
    const { page } = await ouvrir();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('Trop de codes incorrects');
  });
});
