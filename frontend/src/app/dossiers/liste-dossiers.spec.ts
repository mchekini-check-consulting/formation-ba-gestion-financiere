import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SessionService } from '../session/session.service';
import { LigneDossier } from './dossier.service';
import { DELAI_RECHERCHE_MS, ListeDossiers } from './liste-dossiers';

const BOULANGERIE: LigneDossier = {
  id: 1,
  raisonSociale: 'Boulangerie Leroy SARL',
  siren: '812345678',
  nombreFactures: 2,
  etatEspaceClient: 'ACTIF',
};
const GARAGE: LigneDossier = {
  id: 2,
  raisonSociale: 'Garage Morel SAS',
  siren: '798654321',
  nombreFactures: 1,
  etatEspaceClient: 'INVITE',
};
const FLEURS: LigneDossier = {
  id: 3,
  raisonSociale: 'Fleurs Martin EURL',
  siren: '812345679',
  nombreFactures: 0,
  etatEspaceClient: 'NON_INVITE',
};

describe('US-09 Liste des dossiers du cabinet', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [ListeDossiers],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).ouvrir({ jeton: 'jeton', role: 'COLLABORATEUR', nomAffiche: 'Jean Dupont' });
  });

  afterEach(() => http.verify());

  /** La recherche part 0,3 s après la dernière saisie (RG2). */
  function attendreLaRecherche() {
    return new Promise((fin) => setTimeout(fin, DELAI_RECHERCHE_MS + 50));
  }

  async function afficher(lignes: LigneDossier[]) {
    const fixture = TestBed.createComponent(ListeDossiers);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Chargement…');
    http.expectOne('/api/dossiers').flush(lignes);
    await fixture.whenStable();
    return { fixture, page };
  }

  it('RG1 : raison sociale, SIREN, nombre de factures et état de l’espace client', async () => {
    const { page } = await afficher([BOULANGERIE, GARAGE, FLEURS]);

    const lignes = [...page.querySelectorAll('tbody tr')].map((tr) =>
      [...tr.querySelectorAll('td')].map((td) => td.textContent?.trim()),
    );
    expect(lignes).toEqual([
      ['Boulangerie Leroy SARL', '812 345 678', '2 factures', 'Actif'],
      ['Garage Morel SAS', '798 654 321', '1 facture', 'Invité'],
      ['Fleurs Martin EURL', '812 345 679', '0 facture', 'Non invité'],
    ]);
    // Chaque dossier s'ouvre depuis la liste (US-11).
    expect(page.querySelector('tbody a')?.getAttribute('href')).toBe('/dossiers/1');
    expect(page.querySelector('h1')?.textContent).toBe('Dossiers clients');
    expect(page.querySelector('.sous-titre')?.textContent).toBe('Jean Dupont');
  });

  it('CA vide : « Aucun dossier pour le moment » et bouton « Créer un dossier »', async () => {
    const { page } = await afficher([]);

    const vide = page.querySelector('.vide');
    expect(vide?.textContent).toContain('Aucun dossier pour le moment');
    expect(vide?.querySelector('a.bouton')?.textContent?.trim()).toBe('Créer un dossier');
    expect(vide?.querySelector('a.bouton')?.getAttribute('href')).toBe('/dossiers/nouveau');
    expect(page.querySelector('table')).toBeNull();
    expect(page.querySelector('#recherche')).toBeNull();
  });

  it('RG2 : la recherche interroge le serveur après la saisie', async () => {
    const { fixture, page } = await afficher([BOULANGERIE, GARAGE]);

    const champ = page.querySelector<HTMLInputElement>('#recherche')!;
    champ.value = 'mor';
    champ.dispatchEvent(new Event('input'));
    champ.value = ' morel ';
    champ.dispatchEvent(new Event('input'));
    await attendreLaRecherche();

    // Une seule requête, avec le dernier texte saisi.
    const requete = http.expectOne((r) => r.url === '/api/dossiers');
    expect(requete.request.params.get('recherche')).toBe('morel');
    requete.flush([GARAGE]);
    await fixture.whenStable();
    expect(page.querySelectorAll('tbody tr').length).toBe(1);
    expect(page.querySelector('tbody')?.textContent).toContain('Garage Morel SAS');

    // Rien ne correspond : la recherche reste affichée, avec un message.
    champ.value = 'inconnu';
    champ.dispatchEvent(new Event('input'));
    await attendreLaRecherche();
    http.expectOne((r) => r.params.get('recherche') === 'inconnu').flush([]);
    await fixture.whenStable();
    expect(page.textContent).toContain('Aucun dossier ne correspond à votre recherche');
    expect(page.querySelector('#recherche')).not.toBeNull();
  });

  it('RG3 : un compte sans cabinet voit le message du serveur', async () => {
    const fixture = TestBed.createComponent(ListeDossiers);
    await fixture.whenStable();
    http
      .expectOne('/api/dossiers')
      .flush({ message: "Seuls les collaborateurs d'un cabinet ont accès aux dossiers" }, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent).toBe(
      "Seuls les collaborateurs d'un cabinet ont accès aux dossiers",
    );
  });

  it('service indisponible : message, puis la saisie suivante réessaie', async () => {
    const fixture = TestBed.createComponent(ListeDossiers);
    await fixture.whenStable();
    http.expectOne('/api/dossiers').flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('[role=alert]')?.textContent).toContain('momentanément indisponible');

    fixture.componentInstance.chercher('garage');
    await attendreLaRecherche();
    http.expectOne((r) => r.params.get('recherche') === 'garage').flush([GARAGE]);
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')).toBeNull();
    expect(page.querySelector('tbody')?.textContent).toContain('Garage Morel SAS');
  });
});
