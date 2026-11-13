import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { SessionService } from '../session/session.service';
import { CreerCompte, MESSAGE_INDISPONIBLE } from './creer-compte';
import { Formulaire } from './validation';

/** CA1 : formulaire de Claire Martin pour Cabinet Test. */
const claire = (): Formulaire => ({
  nomCabinet: 'Cabinet Test',
  siren: '123 456 789',
  prenom: 'Claire',
  nom: 'Martin',
  email: 'claire.martin@cabinet-test.fr',
  motDePasse: 'Pointeo2026!!',
  nombreDossiers: 120,
  codeParrainage: '',
  cguAcceptees: true,
});

const MESSAGE_SIREN = 'Un cabinet existe déjà avec ce SIREN. Demandez à son administrateur de vous inviter.';

describe('Créer un compte (US-36)', () => {
  let http: HttpTestingController;

  async function configurer(dossiers: string | null = null) {
    await TestBed.configureTestingModule({
      imports: [CreerCompte],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(dossiers ? { dossiers } : {}) } },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  }

  beforeEach(() => sessionStorage.clear());
  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  async function ouvrir() {
    const fixture = TestBed.createComponent(CreerCompte);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const stable = async () => {
      fixture.detectChanges();
      await fixture.whenStable();
    };
    const saisir = async (selecteur: string, valeur: string) => {
      const input = page.querySelector<HTMLInputElement>(selecteur)!;
      input.value = valeur;
      input.dispatchEvent(new Event('input'));
      await stable();
    };
    const remplir = async () => {
      const f = claire();
      await saisir('#nom-cabinet', f.nomCabinet);
      await saisir('#siren', f.siren);
      await saisir('#prenom', f.prenom);
      await saisir('#nom', f.nom);
      await saisir('#email', f.email);
      await saisir('#mot-de-passe', f.motDePasse);
      await saisir('#nombre-dossiers', String(f.nombreDossiers));
      page.querySelector<HTMLInputElement>('input[type=checkbox]')!.click();
      await stable();
    };
    const envoyer = async () => {
      page.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
      await stable();
    };
    const texte = (selecteur: string) => page.querySelector(selecteur)?.textContent?.replace(/\s+/g, ' ').trim();
    /** Étape 1 remplie et envoyée : renvoie la requête au backend. */
    const etape1 = async () => {
      await remplir();
      await envoyer();
      return http.expectOne('/api/inscription');
    };
    return { fixture, page, saisir, remplir, envoyer, texte, etape1, stable };
  }

  it('étape 1 : affiche le formulaire « Vos informations »', async () => {
    await configurer();
    const { page, texte } = await ouvrir();
    expect(texte('h1')).toBe('Créer un compte');
    for (const id of ['nom-cabinet', 'siren', 'prenom', 'nom', 'email', 'mot-de-passe', 'nombre-dossiers', 'code-parrainage']) {
      expect(page.querySelector(`label[for=${id}]`), id).not.toBeNull();
    }
    expect(texte('.cgu')).toContain("J'accepte les conditions générales d'utilisation");
    expect(page.querySelector('[data-testid=tarif]')).toBeNull();
    expect(page.querySelector('.steps__num--actif')?.textContent).toBe('1');
    // 2 étapes : plus d'étape « Code email ».
    expect(page.querySelectorAll('.steps li').length).toBe(2);
    expect(texte('.steps li:nth-child(2) strong')).toBe('Cabinet prêt');
    expect(texte('.etape')).toBe('Étape 1 sur 2');
    expect(texte('button[type=submit]')).toBe('Créer mon cabinet');
  });

  it('RG2 : le nombre de dossiers affiche la tranche et le prix mensuel', async () => {
    await configurer();
    const { saisir, texte } = await ouvrir();
    await saisir('#nombre-dossiers', '120');
    expect(texte('[data-testid=tarif] span')).toBe('Tranche 51 à 150 dossiers · 8 € HT / dossier');
    expect(texte('[data-testid=tarif] strong')).toBe('960 € HT par mois');
    await saisir('#nombre-dossiers', '300');
    expect(texte('[data-testid=tarif] strong')).toBe('2 100 € HT par mois');
  });

  it('reprend le nombre de dossiers du simulateur de la landing page', async () => {
    await configurer('120');
    const { page, texte } = await ouvrir();
    expect(page.querySelector<HTMLInputElement>('#nombre-dossiers')!.value).toBe('120');
    expect(texte('[data-testid=tarif] strong')).toBe('960 € HT par mois');
  });

  it('champs vides : messages sous chaque champ, rien n’est envoyé', async () => {
    await configurer();
    const { page, envoyer, texte } = await ouvrir();
    await envoyer();
    expect(page.querySelectorAll('.field__error').length).toBe(8);
    expect(texte('.cgu + .field__error')).toBe('Vous devez accepter les CGU pour créer votre compte');
    http.expectNone('/api/inscription');
  });

  it('les règles du mot de passe s’affichent au fil de la saisie', async () => {
    await configurer();
    const { page, saisir, stable } = await ouvrir();
    await saisir('#mot-de-passe', 'abc');
    expect(page.querySelectorAll('.rules .ok').length).toBe(1);
    page.querySelector<HTMLButtonElement>('.password__toggle')!.click();
    await stable();
    expect(page.querySelector<HTMLInputElement>('#mot-de-passe')!.type).toBe('text');
  });

  it('CA1 : le formulaire crée le cabinet, connecte Claire et affiche « Cabinet prêt », sans code email', async () => {
    await configurer();
    const { page, etape1, stable, texte } = await ouvrir();
    const requete = await etape1();
    expect(requete.request.method).toBe('POST');
    expect(requete.request.body).toEqual(claire());
    requete.flush({ jeton: 'jeton', role: 'COLLABORATEUR', nomAffiche: 'Claire Martin', nomCabinet: 'Cabinet Test' });
    await stable();

    expect(texte('h1')).toBe('Cabinet Test est prêt');
    expect(texte('[data-testid=bandeau]')).toBe('Compte créé');
    expect(texte('.etape')).toBe('Étape 2 sur 2');
    expect(texte('.form__title p')).toContain('Bienvenue Claire Martin');
    expect(page.querySelector('.steps__num--actif')?.textContent).toBe('2');
    expect(page.querySelector('#code')).toBeNull();
    expect(page.querySelectorAll('.action').length).toBe(3);
    expect(TestBed.inject(SessionService).jeton()).toBe('jeton');
    expect(TestBed.inject(SessionService).utilisateur()).toEqual({
      nomAffiche: 'Claire Martin',
      role: 'COLLABORATEUR',
      administrateur: true,
    });
    expect(page.querySelector('.action a[href="/equipe"]')?.textContent).toBe('Inviter mes collaborateurs');
  });

  it('pendant la création, le bouton est désactivé et un second clic n’envoie rien', async () => {
    await configurer();
    const { page, etape1, stable, texte } = await ouvrir();
    const requete = await etape1();
    expect(texte('button[type=submit]')).toBe('Création du cabinet…');
    expect(page.querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBe(true);
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await stable();
    http.expectNone('/api/inscription');
    requete.flush({ jeton: 'jeton', role: 'COLLABORATEUR', nomAffiche: 'Claire Martin', nomCabinet: 'Cabinet Test' });
    await stable();
  });

  it('CA2 : SIREN déjà utilisé -> message affiché, sous le SIREN aussi', async () => {
    await configurer();
    const { etape1, stable, texte } = await ouvrir();
    (await etape1()).flush(
      { message: MESSAGE_SIREN, erreurs: { siren: MESSAGE_SIREN } },
      { status: 409, statusText: 'Conflict' },
    );
    await stable();
    expect(texte('[data-testid=alerte]')).toBe(MESSAGE_SIREN);
    expect(texte('#siren ~ .field__error')).toBe(MESSAGE_SIREN);
    expect(texte('h1')).toBe('Créer un compte');
  });

  it('erreur inattendue -> message générique', async () => {
    await configurer();
    const { etape1, stable, texte } = await ouvrir();
    (await etape1()).flush(null, { status: 500, statusText: 'Server Error' });
    await stable();
    expect(texte('[data-testid=alerte]')).toBe(MESSAGE_INDISPONIBLE);
  });
});
