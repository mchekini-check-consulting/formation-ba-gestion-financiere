import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { MotDePasseOublie } from './mot-de-passe-oublie';
import { NouveauMotDePasse } from './nouveau-mot-de-passe';
import { evaluerRegles } from './regles';

function saisir(page: HTMLElement, selecteur: string, valeur: string) {
  const input = page.querySelector<HTMLInputElement>(selecteur)!;
  input.value = valeur;
  input.dispatchEvent(new Event('input'));
}

describe('Règles du mot de passe (US-06 RG3)', () => {
  it('évalue chaque règle', () => {
    expect(evaluerRegles('Nouveau#Pointeo2026').every((r) => r.respectee)).toBe(true);
    expect(
      evaluerRegles('bonjour')
        .filter((r) => !r.respectee)
        .map((r) => r.libelle),
    ).toEqual(['12 caractères minimum', 'Une majuscule', 'Un chiffre', 'Un caractère spécial']);
  });
});

describe('MotDePasseOublie (US-06 étape 1)', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MotDePasseOublie],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function ouvrir() {
    const fixture = TestBed.createComponent(MotDePasseOublie);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const envoyer = async () => {
      page.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
      await fixture.whenStable();
    };
    return { fixture, page, envoyer };
  }

  it('email vide -> « Ce champ est obligatoire »', async () => {
    const { page, envoyer } = await ouvrir();
    await envoyer();
    expect(page.textContent).toContain('Ce champ est obligatoire');
    http.expectNone('/api/auth/mot-de-passe-oublie');
  });

  it('RG2 : affiche le message unique et, en mode test, le lien reçu', async () => {
    const { fixture, page, envoyer } = await ouvrir();
    saisir(page, '#email', 'jean.dupont@cabinet-test.fr');
    await envoyer();
    const requete = http.expectOne('/api/auth/mot-de-passe-oublie');
    expect(requete.request.body).toEqual({ email: 'jean.dupont@cabinet-test.fr' });
    requete.flush({ message: 'Si un compte existe, un email vous a été envoyé', jetonDeTest: 'abc' });
    await fixture.whenStable();
    expect(page.querySelector('[role=status]')?.textContent).toContain('Si un compte existe, un email vous a été envoyé');
    // Le lien arrive par email (Mailpit en dev/qualif) : jamais affiché, même si le backend le renvoie.
    expect(page.querySelector('a[href*="reinitialiser-mot-de-passe"]')).toBeNull();
  });

  it('backend injoignable -> message de service indisponible', async () => {
    const { fixture, page, envoyer } = await ouvrir();
    saisir(page, '#email', 'jean.dupont@cabinet-test.fr');
    await envoyer();
    http.expectOne('/api/auth/mot-de-passe-oublie').flush(null, { status: 500, statusText: 'Erreur' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('momentanément impossible');
  });
});

describe('NouveauMotDePasse (US-06 étape 2)', () => {
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  async function ouvrir(jeton: string | null = 'jeton-1') {
    await TestBed.configureTestingModule({
      imports: [NouveauMotDePasse],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(jeton ? { jeton } : {}) } },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(NouveauMotDePasse);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const bouton = () => page.querySelector<HTMLButtonElement>('button[type=submit]');
    return { fixture, page, bouton };
  }

  async function lienValable() {
    const ecran = await ouvrir();
    const requete = http.expectOne('/api/auth/reinitialisation/verifier');
    expect(requete.request.body).toEqual({ jeton: 'jeton-1' });
    requete.flush(null, { status: 204, statusText: 'No Content' });
    await ecran.fixture.whenStable();
    return ecran;
  }

  afterEach(() => http.verify());

  it('CA2 : lien expiré -> « Ce lien a expiré, demandez-en un nouveau »', async () => {
    const { fixture, page } = await ouvrir();
    http
      .expectOne('/api/auth/reinitialisation/verifier')
      .flush({ message: 'Ce lien a expiré, demandez-en un nouveau' }, { status: 410, statusText: 'Gone' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('Ce lien a expiré, demandez-en un nouveau');
    expect(page.querySelector('[role=alert] a')?.getAttribute('href')).toBe('/mot-de-passe-oublie');
  });

  it('sans jeton dans le lien -> lien expiré', async () => {
    const { page } = await ouvrir(null);
    expect(page.textContent).toContain('Ce lien a expiré');
  });

  it('CA3 : « bonjour » -> règles non respectées affichées, Valider désactivé', async () => {
    const { fixture, page, bouton } = await lienValable();
    saisir(page, '#nouveau', 'bonjour');
    saisir(page, '#confirmation', 'bonjour');
    await fixture.whenStable();
    const ko = Array.from(page.querySelectorAll('.rules .ko')).map((li) => li.textContent?.trim());
    expect(ko).toEqual(['✗ 12 caractères minimum', '✗ Une majuscule', '✗ Un chiffre', '✗ Un caractère spécial']);
    expect(page.querySelectorAll('.meter .on').length).toBe(0);
    expect(bouton()!.disabled).toBe(true);
  });

  it('confirmations différentes -> message et Valider désactivé', async () => {
    const { fixture, page, bouton } = await lienValable();
    saisir(page, '#nouveau', 'Nouveau#Pointeo2026');
    saisir(page, '#confirmation', 'Autre#Pointeo2026');
    await fixture.whenStable();
    expect(page.textContent).toContain('Les deux mots de passe ne correspondent pas');
    expect(bouton()!.disabled).toBe(true);
  });

  it('CA1 : mot de passe valide -> changé, retour à la connexion', async () => {
    const { fixture, page, bouton } = await lienValable();
    saisir(page, '#nouveau', 'Nouveau#Pointeo2026');
    saisir(page, '#confirmation', 'Nouveau#Pointeo2026');
    await fixture.whenStable();
    expect(page.querySelectorAll('.meter .on').length).toBe(4);
    bouton()!.click();
    const requete = http.expectOne('/api/auth/reinitialisation');
    expect(requete.request.body).toEqual({ jeton: 'jeton-1', motDePasse: 'Nouveau#Pointeo2026' });
    requete.flush(null, { status: 204, statusText: 'No Content' });
    expect(navigate).toHaveBeenCalledWith(['/connexion'], { queryParams: { raison: 'mot-de-passe-change' } });
  });

  it('refus du serveur : règles, lien expiré entre-temps, erreur inattendue', async () => {
    const { fixture, page, bouton } = await lienValable();
    const valider = async () => {
      saisir(page, '#nouveau', 'Nouveau#Pointeo2026');
      saisir(page, '#confirmation', 'Nouveau#Pointeo2026');
      await fixture.whenStable();
      bouton()!.click();
      return http.expectOne('/api/auth/reinitialisation');
    };

    (await valider()).flush(
      { message: 'Le mot de passe ne respecte pas toutes les règles', reglesNonRespectees: ['64 caractères maximum'] },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('64 caractères maximum');

    (await valider()).flush(null, { status: 500, statusText: 'Erreur' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('momentanément impossible');

    (await valider()).flush({ message: 'expiré' }, { status: 410, statusText: 'Gone' });
    await fixture.whenStable();
    expect(page.textContent).toContain('Ce lien a expiré');
  });
});
