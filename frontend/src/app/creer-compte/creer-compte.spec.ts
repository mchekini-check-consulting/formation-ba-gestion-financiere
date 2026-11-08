import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { SessionService } from '../session/session.service';
import { CreerCompte, MESSAGE_INDISPONIBLE, TITRE_CONFIRMATION } from './creer-compte';
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
    /** Arrivée sur l'étape 2 avec le code de test 482917. */
    const etape2 = async () => {
      (await etape1()).flush({ defi: 'defi', emailMasque: 'c•••••@cabinet-test.fr', codeDeTest: '482917' });
      await stable();
    };
    return { fixture, page, saisir, remplir, envoyer, texte, etape1, etape2, stable };
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
    // Plus de champ mobile : le code part par email.
    expect(page.querySelector('#mobile')).toBeNull();
    expect(texte('.steps li:nth-child(2) strong')).toBe('Code email');
    expect(texte('button[type=submit]')).toBe('Recevoir le code par email');
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

  it('CA1 étapes 1 et 2 : le formulaire envoie le code par email, puis passe au code', async () => {
    await configurer();
    const { page, etape1, stable, texte } = await ouvrir();
    const requete = await etape1();
    expect(requete.request.method).toBe('POST');
    expect(requete.request.body).toEqual(claire());
    requete.flush({ defi: 'defi', emailMasque: 'c•••••@cabinet-test.fr', codeDeTest: '482917' });
    await stable();

    expect(texte('h1')).toBe('Confirmez votre email');
    expect(texte('.form__title p')).toBe(
      'Saisissez le code à 6 chiffres envoyé à claire.martin@cabinet-test.fr. Il est valable 5 minutes.',
    );
    expect(texte('.aside__titre')).toBe('Encore une étape : confirmez votre email.');
    expect(texte('.aside__lead')).toBe(
      'Un code vous sera envoyé par email à chaque connexion. Il protège l’accès aux dossiers et aux données ' +
        'bancaires de vos clients.',
    );
    expect(texte('button.back')).toBe('← Modifier l’email');
    expect(texte('label[for=code]')).toBe('Code reçu par email');
    expect(TestBed.inject(Title).getTitle()).toBe(TITRE_CONFIRMATION);
    expect(TITRE_CONFIRMATION).toBe('Pointéo – Confirmer l’email');
    expect(texte('[data-testid=code-de-test] strong')).toBe('482917');
    expect(page.querySelector('.steps__num--actif')?.textContent).toBe('2');
    expect(texte('.renvoi')).toBe('Renvoyer un code dans 60 s');
  });

  it('CA1 étape 3 : le bon code crée le cabinet, connecte Claire et affiche « Cabinet prêt »', async () => {
    await configurer();
    const { page, saisir, envoyer, etape2, stable, texte } = await ouvrir();
    await etape2();
    await saisir('#code', '482917');
    await envoyer();
    const requete = http.expectOne('/api/inscription/code');
    expect(requete.request.body).toEqual({ defi: 'defi', code: '482917' });
    requete.flush({ jeton: 'jeton', role: 'COLLABORATEUR', nomAffiche: 'Claire Martin', nomCabinet: 'Cabinet Test' });
    await stable();

    expect(texte('h1')).toBe('Cabinet Test est prêt');
    expect(texte('[data-testid=bandeau]')).toBe('Compte créé et email confirmé');
    expect(TestBed.inject(Title).getTitle()).not.toBe(TITRE_CONFIRMATION);
    expect(texte('.form__title p')).toContain('Bienvenue Claire Martin');
    expect(page.querySelector('.steps__num--actif')?.textContent).toBe('3');
    expect(page.querySelectorAll('.action').length).toBe(3);
    expect(TestBed.inject(SessionService).jeton()).toBe('jeton');
    expect(TestBed.inject(SessionService).utilisateur()).toEqual({
      nomAffiche: 'Claire Martin',
      role: 'COLLABORATEUR',
      administrateur: true,
    });
    expect(page.querySelector('.action a[href="/equipe"]')?.textContent).toBe('Inviter mes collaborateurs');
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

  it('code faux -> message et code vidé ; code expiré -> avertissement', async () => {
    await configurer();
    const { page, saisir, envoyer, etape2, stable, texte } = await ouvrir();
    await etape2();
    await saisir('#code', '111111');
    await envoyer();
    http
      .expectOne('/api/inscription/code')
      .flush(
        { message: 'Code incorrect. Il vous reste 2 tentatives.', tentativesRestantes: 2, retourFormulaire: false },
        { status: 401, statusText: 'Unauthorized' },
      );
    await stable();
    expect(texte('[data-testid=alerte]')).toBe('Code incorrect. Il vous reste 2 tentatives.');
    expect(page.querySelector<HTMLInputElement>('#code')!.value).toBe('');

    await saisir('#code', '482917');
    await envoyer();
    http
      .expectOne('/api/inscription/code')
      .flush({ message: 'Ce code a expiré. Demandez un nouveau code.' }, { status: 410, statusText: 'Gone' });
    await stable();
    expect(page.querySelector('.alert--avertissement')?.textContent).toContain('expiré');
  });

  it('trop de codes faux -> retour au formulaire, informations conservées', async () => {
    await configurer();
    const { page, saisir, envoyer, etape2, stable, texte } = await ouvrir();
    await etape2();
    await saisir('#code', '111111');
    await envoyer();
    http
      .expectOne('/api/inscription/code')
      .flush(
        { message: 'Trop de codes incorrects.', tentativesRestantes: 0, retourFormulaire: true },
        { status: 401, statusText: 'Unauthorized' },
      );
    await stable();
    expect(texte('h1')).toBe('Créer un compte');
    expect(texte('[data-testid=alerte]')).toBe('Trop de codes incorrects.');
    expect(page.querySelector<HTMLInputElement>('#siren')!.value).toBe('123 456 789');
  });

  it('« Modifier l’email » revient à l’étape 1', async () => {
    await configurer();
    const { page, etape2, stable, texte } = await ouvrir();
    await etape2();
    page.querySelector<HTMLButtonElement>('button.back')!.click();
    await stable();
    expect(texte('h1')).toBe('Créer un compte');
    expect(page.querySelector<HTMLInputElement>('#email')!.value).toBe('claire.martin@cabinet-test.fr');
  });

  it('renvoi du code après 60 secondes', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    await configurer();
    const { page, etape2, stable, texte } = await ouvrir();
    await etape2();
    vi.advanceTimersByTime(60_000);
    await stable();
    page.querySelector<HTMLButtonElement>('.renvoi button')!.click();
    http.expectOne('/api/inscription/code/renvoyer').flush({ codeDeTest: '555666' });
    await stable();
    expect(texte('[data-testid=code-de-test] strong')).toBe('555666');
    expect(texte('[data-testid=alerte]')).toBe('Un nouveau code vous a été envoyé.');

    vi.advanceTimersByTime(60_000);
    await stable();
    page.querySelector<HTMLButtonElement>('.renvoi button')!.click();
    http
      .expectOne('/api/inscription/code/renvoyer')
      .flush({ message: 'Vous pourrez…', secondesRestantes: 12 }, { status: 429, statusText: 'Too Many Requests' });
    await stable();
    expect(texte('.renvoi')).toBe('Renvoyer un code dans 12 s');
  });

  it('renvoi refusé : demande expirée -> formulaire ; email impossible -> message', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    await configurer();
    const { page, etape2, stable, texte } = await ouvrir();
    await etape2();
    vi.advanceTimersByTime(60_000);
    await stable();
    page.querySelector<HTMLButtonElement>('.renvoi button')!.click();
    http
      .expectOne('/api/inscription/code/renvoyer')
      .flush({ message: 'L’email n’a pas pu être envoyé. Réessayez.' }, { status: 503, statusText: 'Unavailable' });
    await stable();
    expect(texte('[data-testid=alerte]')).toBe('L’email n’a pas pu être envoyé. Réessayez.');

    page.querySelector<HTMLButtonElement>('.renvoi button')!.click();
    http
      .expectOne('/api/inscription/code/renvoyer')
      .flush({ message: 'Recommencez.', retourFormulaire: true }, { status: 401, statusText: 'Unauthorized' });
    await stable();
    expect(texte('h1')).toBe('Créer un compte');
  });
});
