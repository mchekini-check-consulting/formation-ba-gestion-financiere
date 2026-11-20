import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SessionService } from '../session/session.service';
import { LigneFacture, Sens, formaterDepot } from './facture.service';
import { FacturesDossier, MESSAGE_TROP_DE_FICHIERS, resumeImport } from './factures-dossier';

const TICKET: LigneFacture = {
  id: 2,
  nomFichier: 'ticket-fournitures.jpg',
  deposeeLe: '2026-11-08T09:05:00Z',
  deposeePar: 'Jean Dupont',
  statut: 'RECUE',
  nouvelle: true,
  motif: null,
  statutsPossibles: ['EN_COURS', 'A_COMPLETER'],
  source: 'DEPOT_CLIENT',
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
      ['ticket-fournitures.jpgNouvelle', '08/11/2026 à 10:05', 'Jean Dupont', 'Dépôt client', 'Reçue', expect.any(String)],
      ['facture-electricite-octobre.pdf', '05/11/2026 à 10:00', 'Jean Dupont', 'Dépôt client', 'Reçue', expect.any(String)],
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

  describe('US-51 Statut de traitement', () => {
    const EDF: LigneFacture = { ...ELECTRICITE, id: 3, nomFichier: 'facture-edf.pdf' };
    const F107: LigneFacture = {
      ...ELECTRICITE,
      id: 4,
      nomFichier: 'F-107.pdf',
      statut: 'A_COMPLETER',
      motif: 'Facture illisible',
      statutsPossibles: ['EN_COURS'],
    };
    const TRAITEE: LigneFacture = {
      ...ELECTRICITE,
      id: 5,
      nomFichier: 'loyer.pdf',
      statut: 'TRAITEE',
      statutsPossibles: [],
    };
    const EN_COURS: LigneFacture = { ...EDF, statut: 'EN_COURS', statutsPossibles: ['TRAITEE', 'A_COMPLETER'] };

    function menu(page: HTMLElement, id: number) {
      return page.querySelector<HTMLSelectElement>(`#statut-${id}`);
    }

    async function choisir(fixture: ComponentFixture<FacturesDossier>, page: HTMLElement, id: number, statut: string) {
      const select = menu(page, id)!;
      select.value = statut;
      select.dispatchEvent(new Event('change'));
      await fixture.whenStable();
    }

    function bouton(page: HTMLElement, texte: string) {
      return Array.from(page.querySelectorAll<HTMLButtonElement>('button')).find(
        (b) => b.textContent?.trim() === texte,
      )!;
    }

    it('RG4 : pastille de couleur et motif de « À compléter »', async () => {
      const { page } = await afficher('ACHAT', [EDF, F107, TRAITEE]);
      const pastilles = Array.from(page.querySelectorAll('.pastille'));
      expect(pastilles.map((p) => p.textContent)).toEqual(['Reçue', 'À compléter', 'Traitée']);
      expect(pastilles.map((p) => p.className)).toEqual([
        'pastille pastille--recue',
        'pastille pastille--a-completer',
        'pastille pastille--traitee',
      ]);
      expect(page.querySelector('.motif')?.textContent).toBe('Motif : Facture illisible');
      // RG2 : plus rien à proposer pour une facture traitée.
      expect(menu(page, 5)).toBeNull();
    });

    it('RG4 : filtre par statut, et message si aucun résultat', async () => {
      const { fixture, page } = await afficher('ACHAT', [EDF, F107]);
      const filtre = page.querySelector<HTMLSelectElement>('#filtre-statut')!;
      expect(Array.from(filtre.options).map((o) => o.text)).toEqual([
        'Tous les statuts',
        'Reçue',
        'En cours de traitement',
        'Traitée',
        'À compléter',
      ]);
      filtre.value = 'A_COMPLETER';
      filtre.dispatchEvent(new Event('change'));
      await fixture.whenStable();
      expect(lignes(page).map((l) => l[0])).toEqual(['F-107.pdf']);

      filtre.value = 'TRAITEE';
      filtre.dispatchEvent(new Event('change'));
      await fixture.whenStable();
      expect(page.querySelector('table')).toBeNull();
      expect(page.querySelector('.vide')?.textContent).toBe('Aucune facture « Traitée »');

      filtre.value = '';
      filtre.dispatchEvent(new Event('change'));
      await fixture.whenStable();
      expect(lignes(page)).toHaveLength(2);
    });

    it('CA4 : depuis « Reçue », « Traitée » n’est pas proposée', async () => {
      const { page } = await afficher('ACHAT', [EDF]);
      expect(Array.from(menu(page, 3)!.options).map((o) => o.text.trim())).toEqual([
        'Changer le statut…',
        'En cours de traitement',
        'À compléter',
      ]);
    });

    it('CA1 : « En cours de traitement » puis « Traitée »', async () => {
      const { fixture, page } = await afficher('ACHAT', [EDF]);
      await choisir(fixture, page, 3, 'EN_COURS');
      expect(page.querySelector('.motif-saisie')).toBeNull();
      bouton(page, 'Valider').click();
      const appel = http.expectOne('/api/dossiers/42/factures/3/statut');
      expect(appel.request.method).toBe('PATCH');
      expect(appel.request.body).toEqual({ statut: 'EN_COURS', motif: null });
      appel.flush(EN_COURS);
      await fixture.whenStable();
      expect(page.querySelector('.pastille')?.textContent).toBe('En cours de traitement');
      expect(page.querySelector('.changement')).toBeNull();

      await choisir(fixture, page, 3, 'TRAITEE');
      bouton(page, 'Valider').click();
      http.expectOne('/api/dossiers/42/factures/3/statut').flush({ ...EDF, statut: 'TRAITEE', statutsPossibles: [] });
      await fixture.whenStable();
      expect(page.querySelector('.pastille')?.textContent).toBe('Traitée');
      expect(menu(page, 3)).toBeNull();
    });

    it('CA2 : « À compléter » avec le motif « Facture illisible »', async () => {
      const { fixture, page } = await afficher('ACHAT', [EN_COURS]);
      await choisir(fixture, page, 3, 'A_COMPLETER');
      const motif = page.querySelector<HTMLInputElement>('.motif-saisie')!;
      motif.value = '  Facture illisible ';
      motif.dispatchEvent(new Event('input'));
      await fixture.whenStable();
      bouton(page, 'Valider').click();
      const appel = http.expectOne('/api/dossiers/42/factures/3/statut');
      expect(appel.request.body).toEqual({ statut: 'A_COMPLETER', motif: 'Facture illisible' });
      appel.flush({ ...EN_COURS, statut: 'A_COMPLETER', motif: 'Facture illisible', statutsPossibles: ['EN_COURS'] });
      await fixture.whenStable();
      expect(page.querySelector('.motif')?.textContent).toBe('Motif : Facture illisible');
    });

    it('CA3 : « À compléter » sans motif -> « Indiquez le motif », rien n’est envoyé', async () => {
      const { fixture, page } = await afficher('ACHAT', [EDF]);
      await choisir(fixture, page, 3, 'A_COMPLETER');
      bouton(page, 'Valider').click();
      await fixture.whenStable();
      expect(page.querySelector('.erreur-statut')?.textContent).toBe('Indiquez le motif');
      expect(page.querySelector('.pastille')?.textContent).toBe('Reçue');
      http.expectNone('/api/dossiers/42/factures/3/statut');

      // La saisie efface le message ; « Annuler » ferme la saisie.
      const motif = page.querySelector<HTMLInputElement>('.motif-saisie')!;
      motif.value = 'x';
      motif.dispatchEvent(new Event('input'));
      await fixture.whenStable();
      expect(page.querySelector('.erreur-statut')).toBeNull();
      bouton(page, 'Annuler').click();
      await fixture.whenStable();
      expect(page.querySelector('.changement')).toBeNull();

      // Revenir sur « Changer le statut… » annule aussi.
      await choisir(fixture, page, 3, 'EN_COURS');
      await choisir(fixture, page, 3, '');
      expect(page.querySelector('.changement')).toBeNull();
    });

    it('refus du serveur (passage devenu interdit) ou service indisponible', async () => {
      const { fixture, page } = await afficher('ACHAT', [EDF]);
      await choisir(fixture, page, 3, 'EN_COURS');
      bouton(page, 'Valider').click();
      const message = "Ce changement de statut n'est pas autorisé";
      http.expectOne('/api/dossiers/42/factures/3/statut').flush({ message }, { status: 409, statusText: 'Conflict' });
      await fixture.whenStable();
      expect(page.querySelector('.erreur-statut')?.textContent).toBe(message);

      bouton(page, 'Valider').click();
      http.expectOne('/api/dossiers/42/factures/3/statut').flush(null, { status: 503, statusText: 'Unavailable' });
      await fixture.whenStable();
      expect(page.querySelector('.erreur-statut')?.textContent).toContain('momentanément indisponible');
      expect(page.querySelector('.pastille')?.textContent).toBe('Reçue');
    });
  });

  it('date de dépôt à l’heure de Paris', () => {
    expect(formaterDepot('2026-07-01T22:30:00Z')).toBe('02/07/2026 à 00:30');
  });
});

describe('US-52 Importer des factures depuis le dossier', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [FacturesDossier],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).ouvrir({ jeton: 'jeton', role: 'COLLABORATEUR', nomAffiche: 'Lucie Moreau' });
  });

  afterEach(() => http.verify());

  const IMPORTEE: LigneFacture = {
    id: 9,
    nomFichier: 'facture-edf-juin.pdf',
    deposeeLe: '2026-11-20T09:00:00Z',
    deposeePar: 'Lucie Moreau',
    statut: 'RECUE',
    nouvelle: true,
    motif: null,
    statutsPossibles: ['EN_COURS', 'A_COMPLETER'],
    source: 'IMPORT_CABINET',
  };

  function fichier(nom: string, type: string, taille = 10): File {
    return new File([new Uint8Array(taille)], nom, { type });
  }

  async function ouvrir(sens: Sens) {
    const fixture: ComponentFixture<FacturesDossier> = TestBed.createComponent(FacturesDossier);
    fixture.componentRef.setInput('dossierId', 42);
    fixture.componentRef.setInput('sens', sens);
    await fixture.whenStable();
    http.expectOne(`/api/dossiers/42/factures?sens=${sens}`).flush({ factures: [], exemplesPossibles: false });
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const composant = fixture.componentInstance;
    return { fixture, page, composant };
  }

  function message(page: HTMLElement) {
    return page.querySelector('.resultat__message')?.textContent?.trim();
  }

  it('RG1 : le bouton « Importer des factures » est proposé dans chaque onglet', async () => {
    for (const sens of ['ACHAT', 'VENTE'] as Sens[]) {
      const { page } = await ouvrir(sens);
      expect(page.querySelector('.importer')?.textContent).toContain('Importer des factures');
      expect(page.querySelector<HTMLInputElement>('.importer input')?.multiple).toBe(true);
    }
  });

  it('CA1 : « 1 facture importée », puis la facture apparaît « Reçue » et « Import cabinet »', async () => {
    const { fixture, page, composant } = await ouvrir('ACHAT');
    composant.importer([fichier('facture-edf-juin.pdf', 'application/pdf')]);
    await fixture.whenStable();
    expect(page.textContent).toContain('Import en cours…');

    const envoi = http.expectOne('/api/dossiers/42/factures?sens=ACHAT' );
    expect(envoi.request.method).toBe('POST');
    expect((envoi.request.body as FormData).get('fichier')).toBeInstanceOf(File);
    envoi.flush(IMPORTEE, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    http.expectOne('/api/dossiers/42/factures?sens=ACHAT').flush({ factures: [IMPORTEE], exemplesPossibles: false });
    await fixture.whenStable();
    expect(message(page)).toBe('1 facture importée');
    const cellules = Array.from(page.querySelectorAll('tbody td')).map((td) => td.textContent?.trim());
    expect(cellules).toContain('Import cabinet');
    expect(cellules).toContain('Reçue');
  });

  it('CA2 : releve.xlsx refusé avant l’envoi, avec le motif', async () => {
    const { fixture, page, composant } = await ouvrir('VENTE');
    composant.importer([fichier('releve.xlsx', 'application/vnd.ms-excel')]);
    await fixture.whenStable();
    http.expectNone('/api/dossiers/42/factures?sens=VENTE');
    expect(message(page)).toBe('Format non accepté : PDF, JPG ou PNG uniquement');
    expect(page.querySelector('.resultat__fichier')?.textContent).toBe('releve.xlsx');
  });

  it('RG2 : lot mixte, refus du serveur et service indisponible', async () => {
    const { fixture, page, composant } = await ouvrir('ACHAT');
    composant.importer([
      fichier('a.pdf', 'application/pdf'),
      fichier('faux.pdf', 'application/pdf'),
      fichier('c.png', 'image/png'),
      fichier('releve.xlsx', ''),
    ]);
    await fixture.whenStable();
    http.expectOne('/api/dossiers/42/factures?sens=ACHAT').flush(IMPORTEE, { status: 201, statusText: 'Created' });
    await fixture.whenStable();
    http
      .expectOne('/api/dossiers/42/factures?sens=ACHAT')
      .flush({ message: 'Format non accepté : PDF, JPG ou PNG uniquement' }, { status: 400, statusText: 'Bad' });
    await fixture.whenStable();
    http.expectOne('/api/dossiers/42/factures?sens=ACHAT').flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    http.expectOne('/api/dossiers/42/factures?sens=ACHAT').flush({ factures: [IMPORTEE], exemplesPossibles: false });
    await fixture.whenStable();

    expect(message(page)).toBe('1 facture importée, 3 refusées');
    const refus = Array.from(page.querySelectorAll('.resultat__refus li')).map((li) => li.textContent?.trim());
    expect(refus).toEqual([
      'faux.pdf : Format non accepté : PDF, JPG ou PNG uniquement',
      'c.png : Le service est momentanément indisponible. Réessayez.',
      'releve.xlsx : Format non accepté : PDF, JPG ou PNG uniquement',
    ]);
  });

  it('RG2 : plus de 20 fichiers refusés d’un coup, rien n’est envoyé', async () => {
    const { fixture, page, composant } = await ouvrir('ACHAT');
    composant.importer(Array.from({ length: 21 }, (_, i) => fichier(`f${i}.pdf`, 'application/pdf')));
    composant.importer([]);
    await fixture.whenStable();
    expect(message(page)).toBe(MESSAGE_TROP_DE_FICHIERS);
  });

  it('choix des fichiers par le bouton : le champ est vidé après l’import', async () => {
    const { fixture, page, composant } = await ouvrir('ACHAT');
    const champ = page.querySelector<HTMLInputElement>('.importer input') as HTMLInputElement;
    Object.defineProperty(champ, 'files', { value: [fichier('releve.xlsx', '')], configurable: true });
    champ.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    expect(message(page)).toBe('Format non accepté : PDF, JPG ou PNG uniquement');
    expect(composant).toBeTruthy();
  });

  it('CA3 : sans accès au dossier, le bouton n’est pas proposé', async () => {
    const fixture = TestBed.createComponent(FacturesDossier);
    fixture.componentRef.setInput('dossierId', 43);
    fixture.componentRef.setInput('sens', 'ACHAT');
    await fixture.whenStable();
    http.expectOne('/api/dossiers/43/factures?sens=ACHAT').flush(
      { message: 'Dossier introuvable ou accès non autorisé' },
      { status: 404, statusText: 'Not Found' },
    );
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('.importer')).toBeNull();
  });

  it('résumé de l’import', () => {
    const refus = (n: number) => Array.from({ length: n }, (_, i) => ({ nom: `f${i}`, motif: 'Refusé' }));
    expect(resumeImport(2, [])).toBe('2 factures importées');
    expect(resumeImport(0, refus(1))).toBe('Refusé');
    expect(resumeImport(0, refus(2))).toBe('Aucune facture importée, 2 refusées');
    expect(resumeImport(2, refus(1))).toBe('2 factures importées, 1 refusée');
  });
});
