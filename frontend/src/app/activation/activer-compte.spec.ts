import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { SessionService } from '../session/session.service';
import { ActiverCompte, MESSAGE_CGU, MESSAGE_DEJA_ACTIVE, MESSAGE_INVITATION_EXPIREE } from './activer-compte';

const MOT_DE_PASSE = 'Sophie#Pointeo2026';

function saisir(page: HTMLElement, selecteur: string, valeur: string) {
  const input = page.querySelector<HTMLInputElement>(selecteur)!;
  input.value = valeur;
  input.dispatchEvent(new Event('input'));
}

describe('US-08 Activer son compte', () => {
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => sessionStorage.clear());
  afterEach(() => http.verify());

  async function ouvrir(jeton: string | null = 'jeton-1') {
    await TestBed.configureTestingModule({
      imports: [ActiverCompte],
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
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ActiverCompte);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const bouton = () => page.querySelector<HTMLButtonElement>('button[type=submit]')!;
    return { fixture, page, bouton };
  }

  async function lienValable(role = 'DIRIGEANT') {
    const ecran = await ouvrir();
    const verification = http.expectOne('/api/auth/activation/verifier');
    expect(verification.request.body).toEqual({ jeton: 'jeton-1' });
    verification.flush({ nomAffiche: 'Sophie Martin', role });
    await ecran.fixture.whenStable();
    return ecran;
  }

  async function remplir(ecran: Awaited<ReturnType<typeof ouvrir>>, cgu: boolean) {
    saisir(ecran.page, '#mot-de-passe', MOT_DE_PASSE);
    saisir(ecran.page, '#confirmation', MOT_DE_PASSE);
    if (cgu) {
      ecran.page.querySelector<HTMLInputElement>('#cgu')!.click();
    }
    await ecran.fixture.whenStable();
    ecran.bouton().click();
    await ecran.fixture.whenStable();
  }

  it('RG2 et RG3 : dirigeant -> mot de passe, CGU, session ouverte, espace client', async () => {
    const ecran = await lienValable();
    expect(ecran.page.textContent).toContain('Bienvenue Sophie Martin');
    expect(ecran.page.querySelector('input[type=tel]')).toBeNull();
    expect(ecran.bouton().disabled).toBe(true);
    await remplir(ecran, true);

    const activation = http.expectOne('/api/auth/activation');
    expect(activation.request.body).toEqual({ jeton: 'jeton-1', motDePasse: MOT_DE_PASSE, cguAcceptees: true });
    expect(ecran.bouton().textContent).toContain('Activation…');
    activation.flush({ jeton: 'session', role: 'DIRIGEANT', nomAffiche: 'Sophie Martin', administrateur: false });
    await ecran.fixture.whenStable();

    expect(TestBed.inject(SessionService).jeton()).toBe('session');
    expect(navigate).toHaveBeenCalledWith('/espace-client');
  });

  it('collaborateur invité (US-19) -> liste des dossiers', async () => {
    const ecran = await lienValable('COLLABORATEUR');
    await remplir(ecran, true);
    http.expectOne('/api/auth/activation').flush({ jeton: 's', role: 'COLLABORATEUR', nomAffiche: 'Lucie' });
    await ecran.fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith('/dossiers');
  });

  it('RG2 : « Vous devez accepter les conditions d’utilisation », rien n’est envoyé', async () => {
    const ecran = await lienValable();
    await remplir(ecran, false);
    expect(ecran.page.querySelector('.field__error')?.textContent).toBe(MESSAGE_CGU);
    http.expectNone('/api/auth/activation');
    expect(ecran.page.querySelector('a[href="/cgu"]')).not.toBeNull();

    // Cocher la case efface le message.
    ecran.page.querySelector<HTMLInputElement>('#cgu')!.click();
    await ecran.fixture.whenStable();
    expect(ecran.page.querySelector('.field__error')).toBeNull();
  });

  it('refus du serveur : CGU, règles du mot de passe, service indisponible', async () => {
    const ecran = await lienValable();
    await remplir(ecran, true);
    http.expectOne('/api/auth/activation').flush(
      { message: 'x', erreurs: { cgu: MESSAGE_CGU }, reglesNonRespectees: [] },
      { status: 400, statusText: 'Bad Request' },
    );
    await ecran.fixture.whenStable();
    expect(ecran.page.querySelector('.field__error')?.textContent).toBe(MESSAGE_CGU);

    ecran.bouton().click();
    await ecran.fixture.whenStable();
    http.expectOne('/api/auth/activation').flush(
      {
        message: 'x',
        erreurs: { motDePasse: 'Le mot de passe ne respecte pas toutes les règles' },
        reglesNonRespectees: ['Un chiffre'],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await ecran.fixture.whenStable();
    expect(ecran.page.querySelector('[role=alert]')?.textContent).toBe(
      'Le mot de passe ne respecte pas toutes les règles : Un chiffre',
    );

    ecran.bouton().click();
    await ecran.fixture.whenStable();
    http.expectOne('/api/auth/activation').flush(null, { status: 503, statusText: 'Unavailable' });
    await ecran.fixture.whenStable();
    expect(ecran.page.querySelector('[role=alert]')?.textContent).toContain('momentanément impossible');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('RG1 : invitation expirée', async () => {
    const ecran = await ouvrir();
    http.expectOne('/api/auth/activation/verifier').flush(
      { message: MESSAGE_INVITATION_EXPIREE },
      { status: 410, statusText: 'Gone' },
    );
    await ecran.fixture.whenStable();
    expect(ecran.page.querySelector('[role=alert]')?.textContent?.trim()).toBe(
      'Cette invitation a expiré. Demandez à votre cabinet de vous la renvoyer.',
    );
    expect(ecran.page.querySelector('form input')).toBeNull();
  });

  it('lien sans jeton : invitation expirée, aucun appel', async () => {
    const ecran = await ouvrir(null);
    expect(ecran.page.querySelector('[role=alert]')?.textContent).toContain(MESSAGE_INVITATION_EXPIREE);
  });

  it('RG1 : « Votre espace est déjà activé » + « Se connecter »', async () => {
    const ecran = await ouvrir();
    http.expectOne('/api/auth/activation/verifier').flush(
      { message: MESSAGE_DEJA_ACTIVE },
      { status: 409, statusText: 'Conflict' },
    );
    await ecran.fixture.whenStable();
    const alerte = ecran.page.querySelector('[role=alert]')!;
    expect(alerte.textContent).toContain('Votre espace est déjà activé');
    expect(alerte.querySelector('a')?.getAttribute('href')).toBe('/connexion');
    expect(alerte.querySelector('a')?.textContent).toBe('Se connecter');
  });

  it('lien utilisé entre-temps (409 à l’activation) ; confirmation différente', async () => {
    const ecran = await lienValable();
    saisir(ecran.page, '#mot-de-passe', MOT_DE_PASSE);
    saisir(ecran.page, '#confirmation', 'autre');
    await ecran.fixture.whenStable();
    expect(ecran.page.textContent).toContain('Les deux mots de passe ne correspondent pas');
    expect(ecran.bouton().disabled).toBe(true);

    await remplir(ecran, true);
    http.expectOne('/api/auth/activation').flush(null, { status: 409, statusText: 'Conflict' });
    await ecran.fixture.whenStable();
    expect(ecran.page.textContent).toContain(MESSAGE_DEJA_ACTIVE);
  });
});
