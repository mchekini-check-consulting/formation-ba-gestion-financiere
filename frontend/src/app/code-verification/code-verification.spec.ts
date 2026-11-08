import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { SessionService } from '../session/session.service';
import { CodeVerification, MESSAGE_EMAIL_IMPOSSIBLE } from './code-verification';

describe('CodeVerification (US-04)', () => {
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    sessionStorage.clear();
    history.replaceState({ defi: 'defi-1', emailMasque: 'j•••••@cabinet-test.fr', codeDeTest: '482917' }, '');
    await TestBed.configureTestingModule({
      imports: [CodeVerification],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  async function ouvrir() {
    const fixture = TestBed.createComponent(CodeVerification);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const cases = () => Array.from(page.querySelectorAll<HTMLInputElement>('.code input'));
    const taper = async (code: string) => {
      [...code].forEach((chiffre, i) => {
        cases()[i].value = chiffre;
        cases()[i].dispatchEvent(new Event('input'));
      });
      await fixture.whenStable();
    };
    const valider = async () => {
      page.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
      await fixture.whenStable();
      return http.expectOne('/api/auth/code');
    };
    return { fixture, page, cases, taper, valider };
  }

  it('RG1 : affiche l’email masqué, six cases et le code du mode test', async () => {
    const { page, cases } = await ouvrir();
    expect(page.querySelector('[data-testid=email]')?.textContent).toBe('j•••••@cabinet-test.fr');
    expect(page.querySelector('.form__title p')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'Saisissez le code à 6 chiffres envoyé par email à j•••••@cabinet-test.fr. Il est valable 5 minutes. ' +
        'Pensez à regarder vos courriers indésirables.',
    );
    expect(cases().length).toBe(6);
    expect(page.querySelector('[data-testid=code-de-test]')?.textContent).toContain('482917');
    expect(page.querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBe(true);
  });

  it('sans passer par le mot de passe -> retour à la connexion', async () => {
    history.replaceState(null, '');
    await ouvrir();
    expect(navigate).toHaveBeenCalledWith(['/connexion']);
  });

  it('CA1 : bon code -> session ouverte, collaborateur sur ses dossiers', async () => {
    const { taper, valider } = await ouvrir();
    await taper('482917');
    const requete = await valider();
    expect(requete.request.body).toEqual({ defi: 'defi-1', code: '482917' });
    requete.flush({ jeton: 'jeton-session', role: 'COLLABORATEUR', nomAffiche: 'Jean Dupont' });

    expect(TestBed.inject(SessionService).jeton()).toBe('jeton-session');
    expect(navigate).toHaveBeenCalledWith(['/dossiers']);
  });

  it('dirigeant -> espace client', async () => {
    const { taper, valider } = await ouvrir();
    await taper('482917');
    (await valider()).flush({ jeton: 'j', role: 'DIRIGEANT', nomAffiche: 'Sophie Martin' });
    expect(navigate).toHaveBeenCalledWith(['/espace-client']);
  });

  it('CA2 : code faux -> « Code incorrect. Il vous reste 2 tentatives. » et cases vidées', async () => {
    const { fixture, page, cases, taper, valider } = await ouvrir();
    await taper('111111');
    (await valider()).flush(
      { message: 'Code incorrect. Il vous reste 2 tentatives.', tentativesRestantes: 2, retourConnexion: false },
      { status: 401, statusText: 'Unauthorized' },
    );
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('Code incorrect. Il vous reste 2 tentatives.');
    expect(cases().every((c) => c.value === '')).toBe(true);
  });

  it('CA3 : code expiré -> avertissement', async () => {
    const { fixture, page, taper, valider } = await ouvrir();
    await taper('482917');
    (await valider()).flush(
      { message: 'Ce code a expiré. Demandez un nouveau code.', tentativesRestantes: 3, retourConnexion: false },
      { status: 410, statusText: 'Gone' },
    );
    await fixture.whenStable();
    const alerte = page.querySelector('[role=alert]')!;
    expect(alerte.textContent).toContain('Ce code a expiré. Demandez un nouveau code.');
    expect(alerte.classList).toContain('alert--avertissement');
  });

  it('RG3 : trois codes faux -> retour à la connexion avec le message', async () => {
    const { taper, valider } = await ouvrir();
    await taper('111111');
    (await valider()).flush(
      { message: 'Trop de codes incorrects. Reconnectez-vous.', tentativesRestantes: 0, retourConnexion: true },
      { status: 401, statusText: 'Unauthorized' },
    );
    expect(navigate).toHaveBeenCalledWith(['/connexion'], {
      state: { message: 'Trop de codes incorrects. Reconnectez-vous.' },
    });
  });

  it('erreur inattendue -> message générique', async () => {
    const { fixture, page, taper, valider } = await ouvrir();
    await taper('482917');
    (await valider()).flush(null, { status: 500, statusText: 'Erreur' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('momentanément impossible');
  });

  it('coller le code remplit les six cases ; ignore les lettres', async () => {
    const { fixture, page, cases } = await ouvrir();
    const coller = (texte: string) => {
      const evenement = new Event('paste', { cancelable: true }) as ClipboardEvent;
      Object.defineProperty(evenement, 'clipboardData', { value: { getData: () => texte } });
      cases()[0].dispatchEvent(evenement);
    };
    coller('abc');
    coller('48 29 17');
    await fixture.whenStable();
    expect(cases().map((c) => c.value).join('')).toBe('482917');
    expect(page.querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBe(false);
  });

  it('saisie : garde le dernier chiffre, avance, et Retour arrière revient en arrière', async () => {
    const { cases } = await ouvrir();
    cases()[0].value = '4a';
    cases()[0].dispatchEvent(new Event('input'));
    expect(cases()[0].value).toBe('4');
    expect(document.activeElement).toBe(cases()[1]);
    cases()[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'Backspace' }));
    expect(document.activeElement).toBe(cases()[0]);
  });

  it('RG2 : « Renvoyer un code » apparaît après 60 secondes et renvoie un nouveau code', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    const { fixture, page } = await ouvrir();
    expect(page.textContent).toContain('Renvoyer un code dans 60 s');
    vi.advanceTimersByTime(60_000);
    await fixture.whenStable();
    const bouton = page.querySelector<HTMLButtonElement>('.renvoi button')!;
    expect(bouton.textContent).toContain('Renvoyer un code');
    bouton.click();
    const requete = http.expectOne('/api/auth/code/renvoyer');
    expect(requete.request.body).toEqual({ defi: 'defi-1' });
    requete.flush({ codeDeTest: '123456' });
    await fixture.whenStable();
    expect(page.querySelector('[role=status]')?.textContent).toContain('Un nouveau code vous a été envoyé.');
    expect(page.querySelector('[data-testid=code-de-test]')?.textContent).toContain('123456');
    expect(page.textContent).toContain('Renvoyer un code dans 60 s');
  });

  it('RG2 : renvoi refusé (trop tôt, email impossible, demande périmée)', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    const { fixture, page } = await ouvrir();
    const renvoyer = async () => {
      vi.advanceTimersByTime(60_000);
      await fixture.whenStable();
      page.querySelector<HTMLButtonElement>('.renvoi button')!.click();
      return http.expectOne('/api/auth/code/renvoyer');
    };

    (await renvoyer()).flush({ secondesRestantes: 30 }, { status: 429, statusText: 'Too Many' });
    await fixture.whenStable();
    expect(page.textContent).toContain('Renvoyer un code dans 30 s');

    (await renvoyer()).flush({ message: MESSAGE_EMAIL_IMPOSSIBLE }, { status: 503, statusText: 'X' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('L’email n’a pas pu être envoyé. Réessayez.');

    // Sans message du serveur : même texte par défaut.
    (await renvoyer()).flush(null, { status: 503, statusText: 'X' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('L’email n’a pas pu être envoyé. Réessayez.');

    (await renvoyer()).flush({ message: 'Périmée', retourConnexion: true }, { status: 401, statusText: 'X' });
    expect(navigate).toHaveBeenCalledWith(['/connexion'], { state: { message: 'Périmée' } });
  });
});
