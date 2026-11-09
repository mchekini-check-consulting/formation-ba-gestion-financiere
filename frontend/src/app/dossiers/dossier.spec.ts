import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { SessionService } from '../session/session.service';
import { Dossier, MESSAGE_INTROUVABLE } from './dossier';
import { DossierClient } from './dossier.service';

const FLEURS: DossierClient = {
  id: 2,
  raisonSociale: 'Fleurs Martin EURL',
  siren: '812345679',
  creeLe: '2026-11-08T09:00:00Z',
  etatEspaceClient: 'INVITE',
};

describe('US-11 Ouvrir un dossier', () => {
  let http: HttpTestingController;
  let adresse: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  beforeEach(async () => {
    sessionStorage.clear();
    adresse = new BehaviorSubject(convertToParamMap({ id: '2' }));
    await TestBed.configureTestingModule({
      imports: [Dossier],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap: adresse } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).ouvrir({ jeton: 'jeton', role: 'COLLABORATEUR', nomAffiche: 'Jean Dupont' });
  });

  afterEach(() => http.verify());

  async function ouvrir(reponse: DossierClient = FLEURS) {
    const fixture = TestBed.createComponent(Dossier);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Chargement…');
    http.expectOne('/api/dossiers/2').flush(reponse);
    await fixture.whenStable();
    // US-12 : l'onglet Factures fournisseurs charge ses factures.
    factures('ACHAT');
    await fixture.whenStable();
    return { fixture, page };
  }

  function factures(sens: 'ACHAT' | 'VENTE') {
    http.expectOne(`/api/dossiers/2/factures?sens=${sens}`).flush({ factures: [], exemplesPossibles: false });
  }

  function onglets(page: HTMLElement) {
    return Array.from(page.querySelectorAll<HTMLButtonElement>('[role=tab]'));
  }

  it('RG1 : en-tête avec raison sociale, SIREN et état de l\'espace client', async () => {
    const { page } = await ouvrir();
    expect(page.querySelector('h1')?.textContent).toBe('Fleurs Martin EURL');
    expect(page.textContent).toContain('SIREN 812 345 679');
    const etat = page.querySelector('.etat');
    expect(etat?.textContent?.trim()).toBe('Espace client : Invité');
    expect(etat?.classList).toContain('etat--invite');
  });

  it('RG1 : états Non invité et Actif', async () => {
    const nonInvite = await ouvrir({ ...FLEURS, etatEspaceClient: 'NON_INVITE' });
    expect(nonInvite.page.querySelector('.etat')?.textContent?.trim()).toBe('Espace client : Non invité');

    const actif = await ouvrir({ ...FLEURS, etatEspaceClient: 'ACTIF' });
    expect(actif.page.querySelector('.etat')?.classList).toContain('etat--actif');
  });

  it('RG2 : trois onglets, Factures fournisseurs ouvert par défaut', async () => {
    const { fixture, page } = await ouvrir();
    expect(onglets(page).map((o) => o.textContent?.trim())).toEqual([
      'Factures fournisseurs',
      'Factures clients',
      'Modules',
    ]);
    expect(onglets(page).map((o) => o.getAttribute('aria-selected'))).toEqual(['true', 'false', 'false']);
    expect(page.querySelector('[role=tabpanel]')?.textContent).toContain('Aucune facture fournisseur pour le moment');

    onglets(page)[1].click();
    await fixture.whenStable();
    factures('VENTE');
    await fixture.whenStable();
    expect(onglets(page)[1].getAttribute('aria-selected')).toBe('true');
    expect(page.querySelector('[role=tabpanel]')?.textContent).toContain('Aucune facture client pour le moment');

    onglets(page)[2].click();
    await fixture.whenStable();
    expect(page.querySelector('[role=tabpanel]')?.id).toBe('panneau-modules');
  });

  it('RG3 : dossier d\'un autre cabinet ou adresse tapée à la main', async () => {
    for (const status of [404, 403, 400]) {
      const fixture = TestBed.createComponent(Dossier);
      await fixture.whenStable();
      http.expectOne('/api/dossiers/2').flush({ message: MESSAGE_INTROUVABLE }, { status, statusText: 'Refus' });
      await fixture.whenStable();
      const page = fixture.nativeElement as HTMLElement;
      expect(page.querySelector('[role=alert]')?.textContent).toBe('Dossier introuvable ou accès non autorisé');
      expect(page.querySelector('h1')).toBeNull();
    }
  });

  it('service indisponible', async () => {
    const fixture = TestBed.createComponent(Dossier);
    await fixture.whenStable();
    http.expectOne('/api/dossiers/2').flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent).toContain(
      'momentanément indisponible',
    );
  });

  it('autre dossier ouvert : rechargé, onglet par défaut', async () => {
    const { fixture, page } = await ouvrir();
    onglets(page)[2].click();
    await fixture.whenStable();

    adresse.next(convertToParamMap({ id: 'abc' }));
    await fixture.whenStable();
    http.expectOne('/api/dossiers/abc').flush({ message: MESSAGE_INTROUVABLE }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toBe(MESSAGE_INTROUVABLE);

    adresse.next(convertToParamMap({}));
    await fixture.whenStable();
    http.expectOne('/api/dossiers/').flush(FLEURS);
    await fixture.whenStable();
    factures('ACHAT');
    await fixture.whenStable();
    expect(onglets(page)[0].getAttribute('aria-selected')).toBe('true');
  });
});
