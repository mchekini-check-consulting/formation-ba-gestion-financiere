import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SessionService } from '../session/session.service';
import { LigneFacture, Sens, formaterDepot } from './facture.service';
import { FacturesDossier } from './factures-dossier';

const TICKET: LigneFacture = {
  id: 2,
  nomFichier: 'ticket-fournitures.jpg',
  deposeeLe: '2026-11-08T09:05:00Z',
  deposeePar: 'Jean Dupont',
  statut: 'RECUE',
  nouvelle: true,
};
const ELECTRICITE: LigneFacture = {
  ...TICKET,
  id: 1,
  nomFichier: 'facture-electricite-octobre.pdf',
  deposeeLe: '2026-11-05T09:00:00Z',
  nouvelle: false,
};

describe('US-12 Factures d’un dossier', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [FacturesDossier],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).ouvrir({ jeton: 'jeton', role: 'COLLABORATEUR', nomAffiche: 'Jean Dupont' });
  });

  afterEach(() => http.verify());

  async function afficher(sens: Sens, factures: LigneFacture[], exemplesPossibles = false) {
    const fixture: ComponentFixture<FacturesDossier> = TestBed.createComponent(FacturesDossier);
    fixture.componentRef.setInput('dossierId', 42);
    fixture.componentRef.setInput('sens', sens);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Chargement…');
    http.expectOne(`/api/dossiers/42/factures?sens=${sens}`).flush({ factures, exemplesPossibles });
    await fixture.whenStable();
    return { fixture, page };
  }

  function lignes(page: HTMLElement) {
    return Array.from(page.querySelectorAll('tbody tr')).map((tr) =>
      Array.from(tr.querySelectorAll('td')).map((td) => td.textContent?.replace(/\s+/g, ' ').trim()),
    );
  }

  it('RG2 et RG3 : fichier, date de dépôt, déposée par, « Reçue », badge « Nouvelle »', async () => {
    const { page } = await afficher('ACHAT', [TICKET, ELECTRICITE]);
    expect(lignes(page)).toEqual([
      ['ticket-fournitures.jpgNouvelle', '08/11/2026 à 10:05', 'Jean Dupont', 'Reçue'],
      ['facture-electricite-octobre.pdf', '05/11/2026 à 10:00', 'Jean Dupont', 'Reçue'],
    ]);
    expect(page.querySelectorAll('.badge--nouvelle')).toHaveLength(1);
    // US-13 RG1 : un clic sur le fichier ouvre son aperçu.
    expect(page.querySelector('a.fichier')?.getAttribute('href')).toBe('/dossiers/42/factures/2');
    expect(page.querySelector('.exemples')).toBeNull();
  });

  it('CA vide : factures fournisseurs et factures clients', async () => {
    const achats = await afficher('ACHAT', []);
    expect(achats.page.querySelector('.vide')?.textContent).toBe('Aucune facture fournisseur pour le moment');
    expect(achats.page.querySelector('table')).toBeNull();

    const ventes = await afficher('VENTE', []);
    expect(ventes.page.querySelector('.vide')?.textContent).toBe('Aucune facture client pour le moment');
  });

  it('mode test : « Ajouter des factures d’exemple » puis la liste est rechargée', async () => {
    const { fixture, page } = await afficher('ACHAT', [], true);
    const bouton = page.querySelector<HTMLButtonElement>('.exemples');
    expect(bouton?.textContent?.trim()).toBe("Ajouter des factures d'exemple (mode test)");

    bouton?.click();
    await fixture.whenStable();
    expect(bouton?.disabled).toBe(true);
    const ajout = http.expectOne('/api/dossiers/42/factures/exemples');
    expect(ajout.request.method).toBe('POST');
    ajout.flush(null, { status: 201, statusText: 'Created' });
    await fixture.whenStable();
    http.expectOne('/api/dossiers/42/factures?sens=ACHAT').flush({ factures: [TICKET], exemplesPossibles: true });
    await fixture.whenStable();
    expect(lignes(page)).toHaveLength(1);
  });

  it('service indisponible (liste ou ajout des exemples)', async () => {
    const fixture = TestBed.createComponent(FacturesDossier);
    fixture.componentRef.setInput('dossierId', 42);
    fixture.componentRef.setInput('sens', 'VENTE');
    await fixture.whenStable();
    http.expectOne('/api/dossiers/42/factures?sens=VENTE').flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('[role=alert]')?.textContent).toContain('momentanément indisponible');

    const autre = await afficher('ACHAT', [], true);
    autre.page.querySelector<HTMLButtonElement>('.exemples')?.click();
    await autre.fixture.whenStable();
    http.expectOne('/api/dossiers/42/factures/exemples').flush(null, { status: 503, statusText: 'Unavailable' });
    await autre.fixture.whenStable();
    expect(autre.page.querySelector('[role=alert]')?.textContent).toContain('momentanément indisponible');
  });

  it('date de dépôt à l’heure de Paris', () => {
    expect(formaterDepot('2026-07-01T22:30:00Z')).toBe('02/07/2026 à 00:30');
  });
});
