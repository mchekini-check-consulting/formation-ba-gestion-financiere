import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SessionService } from '../session/session.service';
import { EcrituresFacture, montantEcriture } from './ecritures-facture';
import { VueEcriture, VueFacture, modifiable } from './facture.service';

const ECRITURE: VueEcriture = {
  journal: 'AC',
  date: '2026-06-12',
  libelle: 'EDF F-2026-0611',
  genereeLe: '2026-11-26T09:00:00Z',
  genereePar: 'Lucie Moreau',
  modifieeLe: null,
  modifieePar: null,
  exportee: false,
  aJour: true,
  lignes: [
    { numeroCompte: '607', libelleCompte: 'Achats de marchandises', libelle: 'EDF F-2026-0611', debit: 155.33, credit: 0 },
    { numeroCompte: '44566', libelleCompte: null, libelle: 'EDF F-2026-0611 – TVA 20 %', debit: 31.07, credit: 0 },
    { numeroCompte: '401', libelleCompte: 'Fournisseurs', libelle: 'EDF F-2026-0611', debit: 0, credit: 186.4 },
  ],
  totalDebit: 186.4,
  totalCredit: 186.4,
};
const EDF: VueFacture = {
  id: 5,
  nomFichier: 'edf.pdf',
  typeFichier: 'application/pdf',
  sens: 'ACHAT',
  statut: 'TRAITEE',
  lecture: {
    statut: 'LUE',
    typeTiers: 'FOURNISSEUR',
    nomTiers: 'EDF',
    numeroFacture: 'F-2026-0611',
    dateEmission: '2026-06-12',
    montantHt: 155.33,
    montantTva: 31.07,
    tauxTva: 20,
    tauxMultiple: false,
    detailTva: [],
    montantTtc: 186.4,
    compteContrepartie: '607',
    libelleContrepartie: 'Achats de marchandises',
    aVerifier: [],
    aRenseigner: [],
  },
  modification: null,
  ecriture: ECRITURE,
};
const REGENERER = '/api/dossiers/42/factures/5/ecriture';

/** Espaces fines et insécables du format français remplacées par des espaces simples. */
function texte(element: Element | null | undefined): string {
  return (element?.textContent ?? '').replace(/\s+/g, ' ').trim();
}

describe('US-56 Générer les écritures d’une facture fournisseur validée', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [EcrituresFacture],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).ouvrir({ jeton: 'jeton', role: 'COLLABORATEUR', nomAffiche: 'Lucie Moreau' });
  });

  afterEach(() => http.verify());

  async function afficher(facture: VueFacture = EDF) {
    const fixture = TestBed.createComponent(EcrituresFacture);
    fixture.componentRef.setInput('dossierId', '42');
    fixture.componentRef.setInput('facture', facture);
    const modifiees: VueFacture[] = [];
    fixture.componentInstance.modifiee.subscribe((v) => modifiees.push(v));
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement, modifiees };
  }

  function bouton(page: HTMLElement): HTMLButtonElement | null {
    return page.querySelector<HTMLButtonElement>('.actions button');
  }

  it('CA1, RG1 à RG3, RG6 : lignes de l’écriture, totaux équilibrés', async () => {
    const { page } = await afficher();
    expect(texte(page.querySelector('h2'))).toBe('Écritures générées');
    expect(texte(page.querySelector('p.muted'))).toContain('Journal des achats (AC) · 12/06/2026 · EDF F-2026-0611');
    expect(texte(page.querySelector('p.muted'))).toContain('Générée par Lucie Moreau le 26/11/2026 à 10:00');
    const lignes = Array.from(page.querySelectorAll('tbody tr')).map((tr) =>
      Array.from(tr.querySelectorAll('td')).map((td) => texte(td)),
    );
    expect(lignes).toEqual([
      ['607Achats de marchandises', 'EDF F-2026-0611', '155,33 €', ''],
      ['44566', 'EDF F-2026-0611 – TVA 20 %', '31,07 €', ''],
      ['401Fournisseurs', 'EDF F-2026-0611', '', '186,40 €'],
    ]);
    expect(texte(page.querySelector('tfoot tr'))).toBe('Total186,40 €186,40 €');
    expect(page.querySelector('.marque')).toBeNull();
    expect(bouton(page)?.disabled).toBe(false);
  });

  it('US-47 RG4 : écriture corrigée à la main depuis le journal', async () => {
    const { page } = await afficher({
      ...EDF,
      ecriture: { ...ECRITURE, modifieePar: 'Lucie Moreau', modifieeLe: '2026-11-26T10:00:00Z' },
    });
    expect(texte(page.querySelector('p.muted'))).toContain('Corrigée à la main par Lucie Moreau le 26/11/2026 à 11:00');
    expect(page.querySelector('.marque')).toBeNull();
  });

  it('CA2, RG5 : « À re-générer », puis « Re-générer les écritures » : fiche à jour', async () => {
    const { fixture, page, modifiees } = await afficher({ ...EDF, ecriture: { ...ECRITURE, aJour: false } });
    expect(texte(page.querySelector('.marque'))).toBe('À re-générer');

    bouton(page)?.click();
    await fixture.whenStable();
    expect(bouton(page)?.disabled).toBe(true);
    const requete = http.expectOne(REGENERER);
    expect(requete.request.method).toBe('POST');
    requete.flush(EDF);
    await fixture.whenStable();
    expect(modifiees).toEqual([EDF]);
    expect(bouton(page)?.disabled).toBe(false);
  });

  it('CA3 : écriture exportée, bouton désactivé avec la mention « Exportée »', async () => {
    const { page } = await afficher({ ...EDF, ecriture: { ...ECRITURE, exportee: true, aJour: false } });
    expect(texte(page.querySelector('.marque'))).toBe('Exportée');
    expect(bouton(page)?.disabled).toBe(true);
    expect(texte(page.querySelector('.actions'))).toBe('Re-générer les écritures Exportée');
  });

  it('refus du backend ou service indisponible : message affiché', async () => {
    const { fixture, page } = await afficher();
    bouton(page)?.click();
    http
      .expectOne(REGENERER)
      .flush({ message: 'Le compte 401 n’est pas dans le plan comptable du dossier' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    expect(texte(page.querySelector('[role=alert]'))).toBe('Le compte 401 n’est pas dans le plan comptable du dossier');

    bouton(page)?.click();
    http.expectOne(REGENERER).flush(null, { status: 503, statusText: 'Service Unavailable' });
    await fixture.whenStable();
    expect(texte(page.querySelector('[role=alert]'))).toBe('Le service est momentanément indisponible. Réessayez.');
  });

  it('pas encore d’écriture : générée à la validation ; facture traitée sans écriture : à re-générer', async () => {
    const { page } = await afficher({ ...EDF, statut: 'EN_COURS', ecriture: null });
    expect(texte(page.querySelector('p.muted'))).toContain('seront générées à la validation de la facture');
    expect(bouton(page)).toBeNull();
    expect(page.querySelector('table')).toBeNull();

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [EcrituresFacture],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    const traitee = await afficher({ ...EDF, ecriture: null });
    expect(texte(traitee.page.querySelector('p.muted'))).toContain('Aucune écriture pour cette facture');
    expect(bouton(traitee.page)?.disabled).toBe(false);
  });

  it('montants et facture modifiable', () => {
    expect(montantEcriture(0)).toBe('');
    expect(montantEcriture(1234.5).replace(/\s/g, ' ')).toBe('1 234,50 €');
    expect(modifiable(EDF)).toBe(true);
    expect(modifiable({ ...EDF, ecriture: null })).toBe(true);
    expect(modifiable({ ...EDF, ecriture: { ...ECRITURE, exportee: true } })).toBe(false);
    // US-57 : une facture client traitée se corrige aussi tant que son écriture n'est pas exportée.
    expect(modifiable({ ...EDF, sens: 'VENTE' })).toBe(true);
    expect(modifiable({ ...EDF, sens: 'VENTE', ecriture: { ...ECRITURE, exportee: true } })).toBe(false);
    expect(modifiable({ ...EDF, statut: 'RECUE' })).toBe(true);
    expect(modifiable({ ...EDF, lecture: null })).toBe(false);
    expect(modifiable({ ...EDF, lecture: { ...EDF.lecture!, statut: 'EN_COURS' } })).toBe(false);
  });
});

describe('US-57 Générer les écritures d’une facture client validée', () => {
  const VENTE: VueEcriture = {
    ...ECRITURE,
    journal: 'VT',
    libelle: 'Café Lumière F-2026-014',
    lignes: [
      { numeroCompte: '411', libelleCompte: 'Clients', libelle: 'Café Lumière F-2026-014', debit: 1440, credit: 0 },
      { numeroCompte: '706', libelleCompte: 'Prestations de services', libelle: 'Café Lumière F-2026-014', debit: 0, credit: 1200 },
      { numeroCompte: '44571', libelleCompte: 'TVA collectée', libelle: 'Café Lumière F-2026-014 – TVA 20 %', debit: 0, credit: 240 },
    ],
    totalDebit: 1440,
    totalCredit: 1440,
  };
  const F2026014: VueFacture = { ...EDF, sens: 'VENTE', ecriture: VENTE };

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [EcrituresFacture],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  async function afficher(facture: VueFacture): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(EcrituresFacture);
    fixture.componentRef.setInput('dossierId', '42');
    fixture.componentRef.setInput('facture', facture);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('CA1 : 411 au débit, 706 et 44571 au crédit, au journal des ventes', async () => {
    const page = await afficher(F2026014);
    expect(texte(page.querySelector('p.muted'))).toContain('Journal des ventes (VT) · 12/06/2026 · Café Lumière F-2026-014');
    const lignes = Array.from(page.querySelectorAll('tbody tr')).map((tr) =>
      Array.from(tr.querySelectorAll('td')).map((td) => texte(td)),
    );
    expect(lignes).toEqual([
      ['411Clients', 'Café Lumière F-2026-014', '1 440,00 €', ''],
      ['706Prestations de services', 'Café Lumière F-2026-014', '', '1 200,00 €'],
      ['44571TVA collectée', 'Café Lumière F-2026-014 – TVA 20 %', '', '240,00 €'],
    ]);
    expect(texte(page.querySelector('tfoot tr'))).toBe('Total1 440,00 €1 440,00 €');
  });

  it('pas encore validée : comptes de vente annoncés', async () => {
    const page = await afficher({ ...F2026014, statut: 'RECUE', ecriture: null });
    expect(texte(page.querySelector('p.muted'))).toContain('client 411, compte de produit, TVA collectée 44571');
  });
});
