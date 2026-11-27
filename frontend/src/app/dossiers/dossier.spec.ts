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
    // US-12 : l'onglet Factures fournisseurs charge ses factures ; US-20 : comptables du dossier.
    factures('ACHAT');
    comptables();
    await fixture.whenStable();
    return { fixture, page };
  }

  function comptables() {
    http.expectOne('/api/dossiers/2/comptables').flush({
      comptables: [{ id: 5, nomAffiche: 'Lucie Bernard', attribue: true }],
      modifiable: false,
    });
  }

  function factures(sens: 'ACHAT' | 'VENTE') {
    http.expectOne(`/api/dossiers/2/factures?sens=${sens}`).flush({ factures: [] });
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

  it('US-14 RG3 : après l’invitation du dirigeant, l’en-tête passe à « Invité »', async () => {
    const { fixture, page } = await ouvrir({ ...FLEURS, etatEspaceClient: 'NON_INVITE' });
    expect(page.textContent).toContain('Inviter le dirigeant');
    fixture.componentInstance.changerEtat('INVITE');
    await fixture.whenStable();
    expect(page.querySelector('.etat')?.textContent?.trim()).toBe('Espace client : Invité');
    expect(page.textContent).toContain("Renvoyer l'invitation");
  });

  it('RG1 : états Non invité et Actif', async () => {
    const nonInvite = await ouvrir({ ...FLEURS, etatEspaceClient: 'NON_INVITE' });
    expect(nonInvite.page.querySelector('.etat')?.textContent?.trim()).toBe('Espace client : Non invité');

    const actif = await ouvrir({ ...FLEURS, etatEspaceClient: 'ACTIF' });
    expect(actif.page.querySelector('.etat')?.classList).toContain('etat--actif');
  });

  it('RG2 : onglets factures et Banque (US-45 ; les modules sont dans le menu de gauche, US-18), Factures fournisseurs ouvert par défaut', async () => {
    const { fixture, page } = await ouvrir();
    expect(onglets(page).map((o) => o.textContent?.trim())).toEqual([
      'Factures fournisseurs',
      'Factures clients',
      'Banque',
      'Écritures',
      'Plan comptable',
    ]);
    expect(onglets(page).map((o) => o.getAttribute('aria-selected'))).toEqual(['true', 'false', 'false', 'false', 'false']);
    expect(page.querySelector('[role=tabpanel]')?.textContent).toContain('Aucune facture fournisseur pour le moment');

    onglets(page)[1].click();
    await fixture.whenStable();
    factures('VENTE');
    await fixture.whenStable();
    expect(onglets(page)[1].getAttribute('aria-selected')).toBe('true');
    expect(page.querySelector('[role=tabpanel]')?.textContent).toContain('Aucune facture client pour le moment');

    // US-45 : l'onglet Banque charge les transactions du dossier.
    onglets(page)[2].click();
    await fixture.whenStable();
    http.expectOne('/api/dossiers/2/transactions').flush([]);
    await fixture.whenStable();
    expect(onglets(page)[2].getAttribute('aria-selected')).toBe('true');
    expect(page.querySelector('[role=tabpanel]')?.textContent).toContain('Aucune transaction sur cette période');

    // US-47 : l'onglet Écritures charge le journal du dossier.
    onglets(page)[3].click();
    await fixture.whenStable();
    http
      .expectOne('/api/dossiers/2/ecritures')
      .flush({ periode: null, periodes: [], ecritures: [], totalDebit: 0, totalCredit: 0 });
    await fixture.whenStable();
    expect(onglets(page)[3].getAttribute('aria-selected')).toBe('true');
    expect(page.querySelector('[role=tabpanel]')?.textContent).toContain('Aucune écriture sur cette période');

    // US-41 : l'onglet Plan comptable charge le plan du dossier.
    onglets(page)[4].click();
    await fixture.whenStable();
    http.expectOne('/api/dossiers/2/plan-comptable').flush({
      comptes: [{ numero: '401', libelle: 'Fournisseurs', sousCompte: false, actif: true }],
      parDefaut: [{ code: 'BANQUE', libelle: 'Banque', numero: '512' }],
    });
    await fixture.whenStable();
    expect(onglets(page)[4].getAttribute('aria-selected')).toBe('true');
    expect(page.querySelector('[role=tabpanel]')?.textContent).toContain('Fournisseurs');
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
    onglets(page)[1].click();
    await fixture.whenStable();
    factures('VENTE');
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
    comptables();
    await fixture.whenStable();
    expect(onglets(page)[0].getAttribute('aria-selected')).toBe('true');
  });
});
