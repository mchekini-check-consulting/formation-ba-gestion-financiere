import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { InactiviteService } from '../session/inactivite.service';
import { SessionService } from '../session/session.service';
import { Espace } from './espace';
import { Dossier, Dossiers, EspaceClient } from './pages';

describe('Espace connecté (US-07)', () => {
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [Espace, Dossiers, Dossier, EspaceClient],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: '2' })) } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  function connecter(role: 'COLLABORATEUR' | 'DIRIGEANT', nomAffiche: string) {
    TestBed.inject(SessionService).ouvrir({ jeton: 'jeton', role, nomAffiche });
  }

  it('collaborateur : menu « Dossiers », nom et initiales', async () => {
    connecter('COLLABORATEUR', 'Jean Dupont');
    const fixture = TestBed.createComponent(Espace);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('.menu__liens')?.textContent).toContain('Dossiers');
    expect(page.querySelector('[data-testid=nom-utilisateur]')?.textContent).toBe('Jean Dupont');
    expect(page.querySelector('.avatar')?.textContent).toBe('JD');
    expect(page.textContent).toContain('Collaborateur');
    fixture.destroy();
  });

  it('US-19 : menu « Équipe » pour les administrateurs du cabinet seulement', async () => {
    connecter('COLLABORATEUR', 'Jean Dupont');
    const fixture = TestBed.createComponent(Espace);
    await fixture.whenStable();
    const menu = () => (fixture.nativeElement as HTMLElement).querySelector('.menu__liens')?.textContent;
    expect(menu()).not.toContain('Équipe');
    TestBed.inject(SessionService).ouvrir({
      jeton: 'j',
      role: 'COLLABORATEUR',
      nomAffiche: 'Claire Martin',
      administrateur: true,
    });
    await fixture.whenStable();
    expect(menu()).toContain('Équipe');
    fixture.destroy();
  });

  it('dirigeant : menu « Mon espace »', async () => {
    connecter('DIRIGEANT', 'Sophie Martin');
    const fixture = TestBed.createComponent(Espace);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('.menu__liens')?.textContent).toContain('Mon espace');
    fixture.destroy();
  });

  it('CA1 : « Se déconnecter » ferme la session et ramène à la connexion', async () => {
    connecter('COLLABORATEUR', 'Jean Dupont');
    const fixture = TestBed.createComponent(Espace);
    await fixture.whenStable();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.menu__deconnexion')!.click();
    const requete = http.expectOne('/api/auth/deconnexion');
    expect(requete.request.headers.get('Authorization')).toBe('Bearer jeton');
    requete.flush(null);
    expect(TestBed.inject(SessionService).jeton()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/connexion'], { queryParams: { raison: 'deconnexion' } });
  });

  it('RG2 : avertissement « Votre session va expirer dans 2:00 » et ses deux boutons', async () => {
    connecter('COLLABORATEUR', 'Jean Dupont');
    const fixture = TestBed.createComponent(Espace);
    await fixture.whenStable();
    const inactivite = TestBed.inject(InactiviteService);
    inactivite.secondesRestantes.set(120);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('[role=dialog] h2')?.textContent).toContain('Votre session va expirer dans 2:00');

    const resterConnecte = vi.spyOn(inactivite, 'resterConnecte').mockImplementation(() => undefined);
    page.querySelector<HTMLButtonElement>('.dialogue .bouton:not(.bouton--clair)')!.click();
    expect(resterConnecte).toHaveBeenCalled();

    page.querySelector<HTMLButtonElement>('.dialogue .bouton--clair')!.click();
    http.expectOne('/api/auth/deconnexion').flush(null);
    expect(navigate).toHaveBeenCalledWith(['/connexion'], { queryParams: { raison: 'deconnexion' } });
  });

  it('pages : dossiers d’exemple, détail d’un dossier, espace client', async () => {
    connecter('COLLABORATEUR', 'Jean Dupont');
    const dossiers = TestBed.createComponent(Dossiers);
    await dossiers.whenStable();
    const liste = dossiers.nativeElement as HTMLElement;
    expect(liste.querySelectorAll('a.ligne').length).toBe(2);
    expect(liste.textContent).toContain('Boulangerie Leroy SARL');

    const dossier = TestBed.createComponent(Dossier);
    await dossier.whenStable();
    expect((dossier.nativeElement as HTMLElement).querySelector('h1')?.textContent).toBe('Garage Morel SAS');

    connecter('DIRIGEANT', 'Sophie Martin');
    const client = TestBed.createComponent(EspaceClient);
    await client.whenStable();
    expect((client.nativeElement as HTMLElement).querySelector('h1')?.textContent).toBe('Bonjour Sophie Martin');
  });
});
