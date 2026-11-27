import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EcritureJournal, JournalEcritures } from './ecriture.service';
import { JournalEcrituresComponent } from './journal-ecritures';

const EDF: EcritureJournal = {
  id: 7,
  journal: 'AC',
  date: '2026-06-12',
  piece: 'F-2026-0611',
  libelle: 'EDF F-2026-0611',
  exportee: false,
  modifieeLe: null,
  modifieePar: null,
  lignes: [
    { numeroCompte: '607', libelleCompte: 'Achats de marchandises', libelle: 'EDF F-2026-0611', debit: 155.33, credit: 0 },
    { numeroCompte: '44566', libelleCompte: null, libelle: 'EDF F-2026-0611 – TVA 20 %', debit: 31.07, credit: 0 },
    { numeroCompte: '401', libelleCompte: 'Fournisseurs', libelle: 'EDF F-2026-0611', debit: 0, credit: 186.4 },
  ],
  totalDebit: 186.4,
  totalCredit: 186.4,
};
const MAI: EcritureJournal = {
  ...EDF,
  id: 8,
  date: '2026-05-03',
  piece: 'OR-1',
  libelle: 'Orange OR-1',
  exportee: true,
  lignes: [
    { numeroCompte: '626', libelleCompte: null, libelle: 'Orange OR-1', debit: 50, credit: 0 },
    { numeroCompte: '401', libelleCompte: 'Fournisseurs', libelle: 'Orange OR-1', debit: 0, credit: 50 },
  ],
  totalDebit: 50,
  totalCredit: 50,
};
const JUIN: JournalEcritures = {
  periode: '2026-06',
  periodes: [
    { code: '2026-06', libelle: 'Juin 2026' },
    { code: '2026-05', libelle: 'Mai 2026' },
    { code: '2026', libelle: 'Exercice 2026' },
  ],
  ecritures: [EDF],
  totalDebit: 186.4,
  totalCredit: 186.4,
};
const URL = '/api/dossiers/2/ecritures';

function texte(element: Element | null | undefined): string {
  return (element?.textContent ?? '').replace(/\s+/g, ' ').trim();
}

describe('US-47 Consulter le journal et corriger une écriture', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JournalEcrituresComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function afficher(journal: JournalEcritures = JUIN) {
    const fixture = TestBed.createComponent(JournalEcrituresComponent);
    fixture.componentRef.setInput('dossierId', 2);
    await fixture.whenStable();
    http.expectOne(URL).flush(journal);
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  function bouton(page: HTMLElement, libelle: string): HTMLButtonElement | undefined {
    return Array.from(page.querySelectorAll<HTMLButtonElement>('button')).find((b) => texte(b) === libelle);
  }

  function saisir(page: HTMLElement, label: string, valeur: string): void {
    const champ = page.querySelector<HTMLInputElement>(`input[aria-label="${label}"]`)!;
    champ.value = valeur;
    champ.dispatchEvent(new Event('input'));
  }

  async function ouvrir(fixture: { whenStable(): Promise<unknown> }, page: HTMLElement, libelle = 'Modifier') {
    bouton(page, libelle)?.click();
    await fixture.whenStable();
  }

  it('RG1 : journal du dernier mois (date, pièce, libellé, comptes, débit, crédit) et total de la période', async () => {
    const { page } = await afficher();
    expect(texte(page.querySelector('header .muted'))).toBe('Journal de juin 2026 · 1 écriture');
    const options = Array.from(page.querySelectorAll('#filtre-periode option')).map((o) => texte(o));
    expect(options).toEqual(['Juin 2026', 'Mai 2026', 'Exercice 2026']);
    const journaux = Array.from(page.querySelectorAll('#filtre-journal option')).map((o) => texte(o));
    expect(journaux).toEqual(['Tous les journaux', 'AC – Achats']);
    const lignes = Array.from(page.querySelectorAll('tbody tr')).map((tr) =>
      Array.from(tr.querySelectorAll('td')).map((td) => texte(td)),
    );
    expect(lignes).toEqual([
      ['12/06/2026', 'F-2026-0611', 'EDF F-2026-0611', '607 Achats de marchandises', '155,33 €', '', 'Modifier'],
      ['', '', '', '44566', '31,07 €', '', ''],
      ['', '', '', '401 Fournisseurs', '', '186,40 €', ''],
    ]);
    expect(texte(page.querySelector('tfoot tr'))).toBe('Total de la période186,40 €186,40 €');
  });

  it('RG1 : changer de période recharge le journal ; recherche et filtre journal', async () => {
    const { fixture, page } = await afficher();
    const periode = page.querySelector<HTMLSelectElement>('#filtre-periode')!;
    periode.value = '2026';
    periode.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    http.expectOne(`${URL}?periode=2026`).flush({ ...JUIN, periode: '2026', ecritures: [EDF, MAI], totalDebit: 236.4, totalCredit: 236.4 });
    await fixture.whenStable();
    expect(texte(page.querySelector('header .muted'))).toBe('Journal de exercice 2026 · 2 écritures');
    expect(page.querySelectorAll('table:not(.lignes) > tbody').length).toBe(2);

    saisir(page, 'Pièce, libellé ou compte', 'orange');
    await fixture.whenStable();
    expect(page.querySelectorAll('table > tbody').length).toBe(1);
    expect(texte(page.querySelector('tbody'))).toContain('OR-1');

    saisir(page, 'Pièce, libellé ou compte', '626');
    await fixture.whenStable();
    expect(texte(page.querySelector('tbody'))).toContain('OR-1');

    saisir(page, 'Pièce, libellé ou compte', 'introuvable');
    await fixture.whenStable();
    expect(texte(page.querySelector('tbody'))).toBe('Aucune écriture ne correspond à la recherche.');

    saisir(page, 'Pièce, libellé ou compte', '');
    const journal = page.querySelector<HTMLSelectElement>('#filtre-journal')!;
    journal.value = 'AC';
    journal.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    expect(page.querySelectorAll('table > tbody').length).toBe(2);
  });

  it('CA1, RG2, RG4 : 607 remplacé par 6061, enregistré ; « Modifiée par » affiché', async () => {
    const { fixture, page } = await afficher();
    await ouvrir(fixture, page);
    expect(texte(page.querySelector('#titre-modifier'))).toBe('Modifier l’écriture F-2026-0611');
    expect(texte(page.querySelector('.panneau .marque'))).toBe('Non exportée');
    expect(page.querySelector<HTMLInputElement>('input[aria-label="Débit de la ligne 1"]')?.value).toBe('155,33');

    saisir(page, 'Compte de la ligne 1', ' 6061 ');
    await fixture.whenStable();
    bouton(page, 'Enregistrer')?.click();
    await fixture.whenStable();
    expect(bouton(page, 'Enregistrer')?.disabled).toBe(true);
    const requete = http.expectOne(`${URL}/7`);
    expect(requete.request.method).toBe('PUT');
    expect(requete.request.body).toEqual({
      libelle: 'EDF F-2026-0611',
      lignes: [
        { numeroCompte: '6061', debit: 155.33, credit: 0 },
        { numeroCompte: '44566', debit: 31.07, credit: 0 },
        { numeroCompte: '401', debit: 0, credit: 186.4 },
      ],
    });
    const modifiee: EcritureJournal = {
      ...EDF,
      modifieePar: 'Lucie Moreau',
      modifieeLe: '2026-06-12T08:00:00Z',
      lignes: [{ ...EDF.lignes[0], numeroCompte: '6061', libelleCompte: 'Fournitures non stockables' }, ...EDF.lignes.slice(1)],
    };
    requete.flush(modifiee);
    await fixture.whenStable();

    expect(texte(page.querySelector('[role=status]'))).toBe('Écriture enregistrée');
    expect(texte(page.querySelector('.panneau .modification'))).toBe('Modifiée par Lucie Moreau le 12/06/2026 à 10:00');
    expect(texte(page.querySelector('tbody .modifiee'))).toBe('Modifiée par Lucie Moreau le 12/06/2026 à 10:00');
    expect(texte(page.querySelector('tbody tr td:nth-child(4)'))).toBe('6061 Fournitures non stockables');
    expect(texte(page.querySelector('table:not(.lignes) tfoot tr'))).toBe('Total de la période186,40 €186,40 €');

    // Une nouvelle saisie efface « Écriture enregistrée » ; « Annuler » ferme le panneau.
    saisir(page, 'Libellé', 'EDF – facture F-2026-0611');
    await fixture.whenStable();
    expect(page.querySelector('[role=status]')).toBeNull();
    bouton(page, 'Annuler')?.click();
    await fixture.whenStable();
    expect(page.querySelector('.panneau')).toBeNull();
  });

  it('CA2, RG3 : écriture déséquilibrée, « Enregistrer » désactivé, écart affiché ; montant invalide', async () => {
    const { fixture, page } = await afficher();
    await ouvrir(fixture, page);
    saisir(page, 'Débit de la ligne 1', '165,33');
    await fixture.whenStable();
    expect(texte(page.querySelector('[role=alert]'))).toBe('Écriture déséquilibrée : écart de 10,00 €');
    expect(texte(page.querySelector('.lignes tfoot'))).toBe('Total196,40 €186,40 €');
    expect(bouton(page, 'Enregistrer')?.disabled).toBe(true);
    bouton(page, 'Enregistrer')?.click();

    saisir(page, 'Débit de la ligne 1', '155,333');
    await fixture.whenStable();
    expect(texte(page.querySelector('[role=alert]'))).toContain('Chaque ligne a un seul montant');
    saisir(page, 'Débit de la ligne 1', '155,33');
    saisir(page, 'Crédit de la ligne 1', '1');
    await fixture.whenStable();
    expect(texte(page.querySelector('[role=alert]'))).toContain('Chaque ligne a un seul montant');
    saisir(page, 'Crédit de la ligne 1', '');
    saisir(page, 'Compte de la ligne 1', ' ');
    await fixture.whenStable();
    expect(bouton(page, 'Enregistrer')?.disabled).toBe(true);
    saisir(page, 'Compte de la ligne 1', '607');
    saisir(page, 'Libellé', ' ');
    await fixture.whenStable();
    expect(bouton(page, 'Enregistrer')?.disabled).toBe(true);
    saisir(page, 'Libellé', 'EDF');
    await fixture.whenStable();
    expect(bouton(page, 'Enregistrer')?.disabled).toBe(false);
  });

  it('RG3 : refus du backend (compte absent du plan), écriture introuvable, service indisponible', async () => {
    const { fixture, page } = await afficher();
    await ouvrir(fixture, page);
    saisir(page, 'Compte de la ligne 1', '6064');
    await fixture.whenStable();

    bouton(page, 'Enregistrer')?.click();
    http
      .expectOne(`${URL}/7`)
      .flush({ message: 'Le compte 6064 n’est pas dans le plan comptable du dossier' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    expect(texte(page.querySelector('[role=alert]'))).toBe('Le compte 6064 n’est pas dans le plan comptable du dossier');
    expect(texte(page.querySelector('tbody tr td:nth-child(4)'))).toBe('607 Achats de marchandises');

    bouton(page, 'Enregistrer')?.click();
    http.expectOne(`${URL}/7`).flush({ message: 'introuvable' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    expect(texte(page.querySelector('[role=alert]'))).toBe('Écriture introuvable : rechargez le journal.');

    bouton(page, 'Enregistrer')?.click();
    http.expectOne(`${URL}/7`).flush(null, { status: 503, statusText: 'Service Unavailable' });
    await fixture.whenStable();
    expect(texte(page.querySelector('[role=alert]'))).toBe('Le service est momentanément indisponible. Réessayez.');
  });

  it('CA3 : écriture exportée : mention « Exportée », champs grisés, pas d’enregistrement', async () => {
    const { fixture, page } = await afficher({ ...JUIN, ecritures: [MAI] });
    expect(texte(page.querySelector('tbody .action'))).toBe('Exportée Consulter');
    await ouvrir(fixture, page, 'Consulter');
    expect(texte(page.querySelector('#titre-modifier'))).toBe('Écriture OR-1');
    expect(texte(page.querySelector('.panneau .marque'))).toBe('Exportée');
    const champs = Array.from(page.querySelectorAll<HTMLInputElement>('.panneau input'));
    expect(champs.length).toBe(7);
    expect(champs.every((c) => c.disabled)).toBe(true);
    expect(bouton(page, 'Enregistrer')).toBeUndefined();
    bouton(page, 'Fermer')?.click();
    await fixture.whenStable();
    expect(page.querySelector('.panneau')).toBeNull();
  });

  it('état vide et service indisponible', async () => {
    const { page } = await afficher({ periode: null, periodes: [], ecritures: [], totalDebit: 0, totalCredit: 0 });
    expect(texte(page.querySelector('.vide'))).toBe(
      'Aucune écriture sur cette périodeLes écritures apparaissent quand une facture ou une transaction est validée.',
    );

    const fixture = TestBed.createComponent(JournalEcrituresComponent);
    fixture.componentRef.setInput('dossierId', 2);
    await fixture.whenStable();
    http.expectOne(URL).flush(null, { status: 503, statusText: 'Service Unavailable' });
    await fixture.whenStable();
    expect(texte((fixture.nativeElement as HTMLElement).querySelector('[role=alert]'))).toBe(
      'Le service est momentanément indisponible. Réessayez.',
    );
  });
});
