import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Cabinet } from '../cabinets';
import { CabinetsPage, messageErreur } from './cabinets-page';

const URL = '/api/cabinets/cabinets';
const MARTIN: Cabinet = {
  id: 1,
  nom: 'Cabinet Martin',
  siren: '812345678',
  email: 'contact@martin.fr',
  nombreDossiers: 120,
  creeLe: '',
};

describe('CabinetsPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<CabinetsPage>;
  let page: HTMLElement;

  const champ = (nom: string) => page.querySelector<HTMLInputElement>(`input[name=${nom}]`)!;
  const saisir = (nom: string, valeur: string) => {
    champ(nom).value = valeur;
    champ(nom).dispatchEvent(new Event('input'));
  };
  const bouton = (texte: string, dans: ParentNode = page) =>
    Array.from(dans.querySelectorAll('button')).find((b) => b.textContent?.trim() === texte)!;
  const stable = () => fixture.whenStable();

  async function ouvrir(cabinets: Cabinet[], dossiers?: string) {
    fixture = TestBed.createComponent(CabinetsPage);
    if (dossiers !== undefined) {
      fixture.componentRef.setInput('dossiers', dossiers);
    }
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    http.expectOne(URL).flush(cabinets);
    await stable();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  it('liste les cabinets inscrits', async () => {
    await ouvrir([MARTIN]);
    expect(page.querySelector('.cabinet')?.textContent).toContain('Cabinet Martin');
    expect(page.querySelector('.cabinet')?.textContent).toContain('120 dossiers');
  });

  it('affiche un message quand aucun cabinet n’est inscrit', async () => {
    await ouvrir([]);
    expect(page.querySelector('.vide')?.textContent).toContain('Aucun cabinet');
  });

  it('crée un compte et l’ajoute à la liste', async () => {
    await ouvrir([]);
    saisir('nom', 'Cabinet Martin');
    saisir('siren', '812 345 678');
    saisir('email', 'contact@martin.fr');
    saisir('nombreDossiers', '120');
    await stable();
    page.querySelector('form')!.dispatchEvent(new Event('submit'));

    const requete = http.expectOne({ method: 'POST', url: URL });
    expect(requete.request.body).toEqual({
      nom: 'Cabinet Martin',
      siren: '812 345 678',
      email: 'contact@martin.fr',
      nombreDossiers: 120,
    });
    requete.flush(MARTIN);
    await stable();

    expect(page.querySelectorAll('.cabinet').length).toBe(1);
    expect(page.querySelector('[role=status]')?.textContent).toContain('est créé');
    expect(champ('nom').value).toBe('');
  });

  it('pré-remplit le nombre de dossiers venant du simulateur', async () => {
    await ouvrir([], '300');
    expect(champ('nombreDossiers').value).toBe('300');
  });

  it('ignore un nombre de dossiers invalide dans l’adresse', async () => {
    await ouvrir([], 'abc');
    expect(champ('nombreDossiers').value).toBe('0');
  });

  it('modifie un cabinet puis revient en création', async () => {
    await ouvrir([MARTIN]);
    bouton('Modifier').click();
    await stable();
    expect(page.querySelector('h1')?.textContent).toContain('Modifier le cabinet');
    expect(champ('nom').value).toBe('Cabinet Martin');

    saisir('nom', 'Cabinet Martin & Associés');
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    const requete = http.expectOne({ method: 'PUT', url: `${URL}/1` });
    expect(requete.request.body.nom).toBe('Cabinet Martin & Associés');
    requete.flush({ ...MARTIN, nom: 'Cabinet Martin & Associés' });
    await stable();

    expect(page.querySelector('.cabinet')?.textContent).toContain('Associés');
    expect(page.querySelector('h1')?.textContent).toContain('Créer le compte');
  });

  it('annule une modification', async () => {
    await ouvrir([MARTIN]);
    bouton('Modifier').click();
    await stable();
    bouton('Annuler').click();
    await stable();
    expect(champ('nom').value).toBe('');
    expect(page.querySelector('h1')?.textContent).toContain('Créer le compte');
  });

  it('supprime un cabinet après confirmation, y compris celui en cours de modification', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    await ouvrir([MARTIN]);
    bouton('Modifier').click();
    await stable();
    bouton('Supprimer').click();
    http.expectOne({ method: 'DELETE', url: `${URL}/1` }).flush(null);
    await stable();
    expect(page.querySelectorAll('.cabinet').length).toBe(0);
    expect(page.querySelector('[role=status]')?.textContent).toContain('est supprimé');
    expect(page.querySelector('h1')?.textContent).toContain('Créer le compte');
  });

  it('supprime un autre cabinet sans quitter la modification en cours', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    await ouvrir([MARTIN, { ...MARTIN, id: 2, nom: 'Autre' }]);
    bouton('Modifier').click();
    await stable();
    bouton('Supprimer', page.querySelectorAll('.cabinet')[1]).click();
    http.expectOne({ method: 'DELETE', url: `${URL}/2` }).flush(null);
    await stable();
    expect(champ('nom').value).toBe('Cabinet Martin');
  });

  it('ne supprime rien sans confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    await ouvrir([MARTIN]);
    bouton('Supprimer').click();
    http.expectNone({ method: 'DELETE' });
    expect(page.querySelectorAll('.cabinet').length).toBe(1);
  });

  it('explique les erreurs du backend', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    await ouvrir([MARTIN]);
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    http.expectOne({ method: 'POST', url: URL }).flush('', { status: 409, statusText: 'Conflict' });
    await stable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('existe déjà avec ce SIREN');

    bouton('Modifier').click();
    await stable();
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    http
      .expectOne({ method: 'PUT', url: `${URL}/1` })
      .flush('', { status: 400, statusText: 'Bad Request' });
    await stable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('vérifiez');

    bouton('Supprimer').click();
    http
      .expectOne({ method: 'DELETE', url: `${URL}/1` })
      .flush('', { status: 404, statusText: 'Not Found' });
    await stable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain("n'existe plus");
  });

  it('signale un backend indisponible au chargement', async () => {
    fixture = TestBed.createComponent(CabinetsPage);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    http.expectOne(URL).flush('', { status: 503, statusText: 'Unavailable' });
    await stable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('service indisponible (503)');
  });

  it('formule un message pour chaque erreur', () => {
    const erreur = (status: number) => new HttpErrorResponse({ status });
    expect(messageErreur('Création', erreur(500))).toContain('(500)');
  });
});
