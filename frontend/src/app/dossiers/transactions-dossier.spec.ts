import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionService } from '../session/session.service';
import {
  Transaction,
  duMois,
  filtrer,
  formaterDate,
  formaterMontant,
  moisCourant,
  totaux,
} from './transaction.service';
import { TransactionsDossier } from './transactions-dossier';

// CA1 : dossier Boulangerie Dupont, en juin, encaissement CB de 2 400 € et prélèvement EDF de 186,40 €.
const ENCAISSEMENT: Transaction = {
  date: '2026-06-20',
  libelle: 'CB Encaissements caisse',
  tiers: 'Encaissements carte bancaire',
  montant: 2400,
  sens: 'ENTREE',
  source: 'BANQUE',
};
const EDF: Transaction = {
  date: '2026-06-05',
  libelle: 'PRLV SEPA EDF - Facture électricité juin',
  tiers: 'EDF',
  montant: 186.4,
  sens: 'SORTIE',
  source: 'BANQUE',
};
const MAI: Transaction = { ...EDF, date: '2026-05-31', libelle: 'PRLV SEPA EDF - mai', montant: 170 };

/** Espaces insécables du format français -> espaces simples, pour comparer les textes. */
function texte(element: Element | null | undefined): string {
  return element?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
}

describe('US-45 Transactions entrées et sorties d’un dossier', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [TransactionsDossier],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).ouvrir({ jeton: 'jeton', role: 'COLLABORATEUR', nomAffiche: 'Lucie Bernard' });
  });

  afterEach(() => http.verify());

  async function afficher(transactions: Transaction[]) {
    const fixture: ComponentFixture<TransactionsDossier> = TestBed.createComponent(TransactionsDossier);
    fixture.componentRef.setInput('dossierId', 42);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Chargement…');
    http.expectOne('/api/dossiers/42/transactions').flush(transactions);
    await fixture.whenStable();
    return { fixture, page };
  }

  async function choisirMois(fixture: ComponentFixture<TransactionsDossier>, page: HTMLElement, mois: string) {
    const champ = page.querySelector<HTMLInputElement>('input.mois');
    if (champ) {
      champ.value = mois;
      champ.dispatchEvent(new Event('change'));
    }
    await fixture.whenStable();
  }

  async function chercher(fixture: ComponentFixture<TransactionsDossier>, page: HTMLElement, recherche: string) {
    const champ = page.querySelector<HTMLInputElement>('input.recherche');
    if (champ) {
      champ.value = recherche;
      champ.dispatchEvent(new Event('input'));
    }
    await fixture.whenStable();
  }

  async function filtreSens(fixture: ComponentFixture<TransactionsDossier>, page: HTMLElement, libelle: string) {
    Array.from(page.querySelectorAll<HTMLButtonElement>('.filtre'))
      .find((b) => b.textContent?.trim() === libelle)
      ?.click();
    await fixture.whenStable();
  }

  function lignes(page: HTMLElement) {
    return Array.from(page.querySelectorAll('tbody tr')).map((tr) => Array.from(tr.querySelectorAll('td')).map(texte));
  }

  function totauxAffiches(page: HTMLElement) {
    return Array.from(page.querySelectorAll('.total')).map(
      (total) => `${texte(total.querySelector('dt'))} ${texte(total.querySelector('dd'))}`,
    );
  }

  it('CA1 et RG1 : date, libellé, tiers, montant et sens, avec les totaux de juin', async () => {
    const { fixture, page } = await afficher([ENCAISSEMENT, EDF, MAI]);
    await choisirMois(fixture, page, '2026-06');
    expect(lignes(page)).toEqual([
      ['20/06/2026', 'CB Encaissements caisse', 'Encaissements carte bancaire', '2 400,00 €', 'Entrée'],
      ['05/06/2026', 'PRLV SEPA EDF - Facture électricité juin', 'EDF', '186,40 €', 'Sortie'],
    ]);
    // Entrée en vert, Sortie en rouge.
    const [entree, sortie] = Array.from(page.querySelectorAll('tbody tr'));
    expect(entree.classList).toContain('entree');
    expect(sortie.classList).toContain('sortie');
    expect(totauxAffiches(page)).toEqual(['Entrées 2 400,00 €', 'Sorties 186,40 €', 'Solde 2 213,60 €']);
    expect(page.querySelector('.demonstration')).toBeNull();
  });

  it('CA2 : filtre « Sorties », les totaux restent ceux de la période', async () => {
    const { fixture, page } = await afficher([ENCAISSEMENT, EDF]);
    await choisirMois(fixture, page, '2026-06');
    await filtreSens(fixture, page, 'Sorties');
    expect(lignes(page).map((l) => l[1])).toEqual(['PRLV SEPA EDF - Facture électricité juin']);
    expect(page.querySelector('.filtre[aria-pressed=true]')?.textContent?.trim()).toBe('Sorties');
    expect(totauxAffiches(page)).toEqual(['Entrées 2 400,00 €', 'Sorties 186,40 €', 'Solde 2 213,60 €']);

    await filtreSens(fixture, page, 'Entrées');
    expect(lignes(page).map((l) => l[1])).toEqual(['CB Encaissements caisse']);
    await filtreSens(fixture, page, 'Toutes');
    expect(lignes(page)).toHaveLength(2);
  });

  it('RG2 : recherche dans le libellé, sans tenir compte des majuscules ni des accents', async () => {
    const { fixture, page } = await afficher([ENCAISSEMENT, EDF]);
    await choisirMois(fixture, page, '2026-06');
    await chercher(fixture, page, 'electricite');
    expect(lignes(page).map((l) => l[2])).toEqual(['EDF']);
    await chercher(fixture, page, 'introuvable');
    expect(page.querySelector('.vide')?.textContent).toBe('Aucune transaction sur cette période');
  });

  it('CA3 : aucune transaction en juillet, totaux à 0,00 €', async () => {
    const { fixture, page } = await afficher([ENCAISSEMENT, EDF]);
    await choisirMois(fixture, page, '2026-07');
    expect(page.querySelector('.vide')?.textContent).toBe('Aucune transaction sur cette période');
    expect(page.querySelector('table')).toBeNull();
    expect(totauxAffiches(page)).toEqual(['Entrées 0,00 €', 'Sorties 0,00 €', 'Solde 0,00 €']);
  });

  it('RG2 : mois en cours par défaut ; champ effacé = retour au mois en cours ; bouchon signalé', async () => {
    const aujourdhui = `${moisCourant()}-01`;
    const { fixture, page } = await afficher([{ ...EDF, date: aujourdhui, source: 'BOUCHON' }, MAI]);
    expect(page.querySelector<HTMLInputElement>('input.mois')?.value).toBe(moisCourant());
    expect(lignes(page)).toHaveLength(1);
    expect(page.querySelector('.total--solde')?.classList).toContain('negatif');
    expect(page.querySelector('.demonstration')?.textContent).toContain('démonstration');

    await choisirMois(fixture, page, '2026-05');
    expect(lignes(page).map((l) => l[1])).toEqual(['PRLV SEPA EDF - mai']);
    await choisirMois(fixture, page, '');
    expect(page.querySelector<HTMLInputElement>('input.mois')?.value).toBe(moisCourant());
    expect(lignes(page)).toHaveLength(1);
  });

  it('service indisponible', async () => {
    const fixture = TestBed.createComponent(TransactionsDossier);
    fixture.componentRef.setInput('dossierId', 42);
    await fixture.whenStable();
    http.expectOne('/api/dossiers/42/transactions').flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('[role=alert]')?.textContent).toContain('momentanément indisponible');
  });

  it('règles de calcul et de format', () => {
    expect(moisCourant(new Date('2026-06-30T22:30:00Z'))).toBe('2026-07');
    expect(duMois([ENCAISSEMENT, MAI], '2026-05')).toEqual([MAI]);
    expect(filtrer([ENCAISSEMENT, EDF], 'TOUTES', '  ')).toHaveLength(2);
    // Centimes : 0,10 + 0,20 donne bien 0,30.
    expect(totaux([{ ...ENCAISSEMENT, montant: 0.1 }, { ...ENCAISSEMENT, montant: 0.2 }]).entrees).toBe(0.3);
    expect(totaux([EDF])).toEqual({ entrees: 0, sorties: 186.4, solde: -186.4 });
    expect(formaterMontant(2213.6).replace(/\s+/g, ' ')).toBe('2 213,60 €');
    expect(formaterDate('2026-06-05')).toBe('05/06/2026');
  });
});
