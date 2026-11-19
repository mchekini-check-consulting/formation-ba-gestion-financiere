import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { SessionService } from '../session/session.service';
import { MESSAGE_AUCUN_COMPTE, PlanComptableDossier } from './plan-comptable-dossier';
import { CompteComptable, PlanComptable, filtrerComptes, trierComptes } from './plan-comptable.service';

const URL = '/api/dossiers/42/plan-comptable';
const FOURNISSEURS: CompteComptable = { numero: '401', libelle: 'Fournisseurs', sousCompte: false, actif: true };
const BANQUE: CompteComptable = { numero: '512', libelle: 'Banque', sousCompte: false, actif: true };
const MOULINS: CompteComptable = { numero: '401MOULINS', libelle: 'Moulins Bertrand', sousCompte: true, actif: true };
// Plan de Boulangerie Leroy SARL.
const PLAN: PlanComptable = {
  comptes: [FOURNISSEURS, BANQUE],
  parDefaut: [
    { code: 'ACHAT', libelle: 'Achat', numero: '607' },
    { code: 'VENTE', libelle: 'Vente', numero: '707' },
    { code: 'FRAIS_BANCAIRES', libelle: 'Frais bancaires', numero: '627' },
    { code: 'BANQUE', libelle: 'Banque', numero: '512' },
  ],
};

function texte(element: Element | null | undefined): string {
  return element?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
}

describe('US-41 Plan comptable du dossier', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [PlanComptableDossier],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).ouvrir({ jeton: 'jeton', role: 'COLLABORATEUR', nomAffiche: 'Lucie Bernard' });
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  async function afficher(plan: PlanComptable = PLAN) {
    const fixture: ComponentFixture<PlanComptableDossier> = TestBed.createComponent(PlanComptableDossier);
    fixture.componentRef.setInput('dossierId', 42);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Chargement…');
    http.expectOne(URL).flush(plan);
    await fixture.whenStable();
    return { fixture, page };
  }

  function lignes(page: HTMLElement): string[] {
    return Array.from(page.querySelectorAll('tbody tr')).map((tr) => texte(tr.querySelector('td')));
  }

  async function saisir(fixture: ComponentFixture<PlanComptableDossier>, page: HTMLElement, numero: string, libelle: string) {
    const champNumero = page.querySelector<HTMLInputElement>('#numero-compte');
    const champLibelle = page.querySelector<HTMLInputElement>('#libelle-compte');
    if (champNumero && champLibelle) {
      champNumero.value = numero;
      champNumero.dispatchEvent(new Event('input'));
      champLibelle.value = libelle;
      champLibelle.dispatchEvent(new Event('input'));
    }
    page.querySelector('form')?.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('RG1, RG2 : comptes du dossier et comptes par défaut des catégories et de la banque', async () => {
    const { page } = await afficher();
    expect(lignes(page)).toEqual(['401', '512']);
    expect(Array.from(page.querySelectorAll('.par-defaut li')).map((li) => texte(li))).toEqual([
      'Achat607',
      'Vente707',
      'Frais bancaires627',
      'Banque512',
    ]);
    // Un compte du plan général ne se supprime pas.
    expect(page.querySelector('.lien--danger')).toBeNull();
  });

  it('CA1 : Lucie crée le compte 401MOULINS « Moulins Bertrand »', async () => {
    const { fixture, page } = await afficher();
    await saisir(fixture, page, '401moulins', 'Moulins Bertrand');

    const requete = http.expectOne(URL);
    expect(requete.request.method).toBe('POST');
    expect(requete.request.body).toEqual({ numero: '401moulins', libelle: 'Moulins Bertrand' });
    requete.flush(MOULINS, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(lignes(page)).toEqual(['401', '401MOULINS', '512']);
    expect(texte(page.querySelector('.confirmation'))).toBe('Compte 401MOULINS « Moulins Bertrand » ajouté au plan');
    expect(page.querySelector<HTMLInputElement>('#numero-compte')?.value).toBe('');
  });

  it('CA2 : 9001 -> « Numéro de compte invalide » sous le champ', async () => {
    const { fixture, page } = await afficher();
    await saisir(fixture, page, '9001', 'Test');

    http.expectOne(URL).flush(
      { message: 'Certains champs sont à corriger', erreurs: { numero: 'Numéro de compte invalide' } },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(texte(page.querySelector('.erreur'))).toBe('Numéro de compte invalide');
    expect(page.querySelector('#numero-compte')?.getAttribute('aria-invalid')).toBe('true');
    expect(lignes(page)).toEqual(['401', '512']);
  });

  it('service indisponible à la création', async () => {
    const { fixture, page } = await afficher();
    await saisir(fixture, page, '401A', 'Test');
    http.expectOne(URL).flush(null, { status: 500, statusText: 'Erreur' });
    await fixture.whenStable();
    expect(texte(page.querySelector('.alerte'))).toBe('Le service est momentanément indisponible. Réessayez.');
  });

  it('RG3 : désactiver puis réactiver un compte', async () => {
    const { fixture, page } = await afficher();
    page.querySelectorAll<HTMLButtonElement>('tbody .lien')[1].click();
    const requete = http.expectOne(`${URL}/512`);
    expect(requete.request.method).toBe('PATCH');
    expect(requete.request.body).toEqual({ actif: false });
    requete.flush({ ...BANQUE, actif: false });
    await fixture.whenStable();

    const ligne = page.querySelectorAll('tbody tr')[1];
    expect(ligne.classList).toContain('inactif');
    expect(texte(ligne.querySelector('.badge'))).toBe('Désactivé');
    expect(texte(ligne.querySelector('.lien'))).toBe('Réactiver');
  });

  it('RG3 : supprimer un sous-compte après confirmation', async () => {
    const confirmer = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    const { fixture, page } = await afficher({ ...PLAN, comptes: [FOURNISSEURS, MOULINS, BANQUE] });

    const supprimer = () => page.querySelector<HTMLButtonElement>('.lien--danger')?.click();
    supprimer();
    http.expectNone(`${URL}/401MOULINS`);

    supprimer();
    expect(confirmer).toHaveBeenCalledWith('Supprimer le compte 401MOULINS « Moulins Bertrand » ?');
    const requete = http.expectOne(`${URL}/401MOULINS`);
    expect(requete.request.method).toBe('DELETE');
    requete.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();
    expect(lignes(page)).toEqual(['401', '512']);
  });

  it('refus du backend affiché tel quel', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const { fixture, page } = await afficher({ ...PLAN, comptes: [MOULINS] });
    page.querySelector<HTMLButtonElement>('.lien--danger')?.click();
    http
      .expectOne(`${URL}/401MOULINS`)
      .flush({ message: 'Compte introuvable dans le plan du dossier' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    expect(texte(page.querySelector('.alerte'))).toBe('Compte introuvable dans le plan du dossier');
  });

  it('recherche dans le numéro ou le libellé', async () => {
    const { fixture, page } = await afficher();
    const champ = page.querySelector<HTMLInputElement>('.recherche');
    if (champ) {
      champ.value = 'fournis';
      champ.dispatchEvent(new Event('input'));
    }
    await fixture.whenStable();
    expect(lignes(page)).toEqual(['401']);

    if (champ) {
      champ.value = 'zzz';
      champ.dispatchEvent(new Event('input'));
    }
    await fixture.whenStable();
    expect(texte(page.querySelector('.vide'))).toBe(MESSAGE_AUCUN_COMPTE);
  });

  it('plan indisponible', async () => {
    const fixture = TestBed.createComponent(PlanComptableDossier);
    fixture.componentRef.setInput('dossierId', 42);
    await fixture.whenStable();
    http.expectOne(URL).flush(null, { status: 500, statusText: 'Erreur' });
    await fixture.whenStable();
    expect(texte((fixture.nativeElement as HTMLElement).querySelector('.alerte'))).toBe(
      'Le service est momentanément indisponible. Réessayez.',
    );
  });

  it('filtrerComptes et trierComptes', () => {
    expect(filtrerComptes([FOURNISSEURS, BANQUE], ' ')).toEqual([FOURNISSEURS, BANQUE]);
    expect(filtrerComptes([FOURNISSEURS, BANQUE], '512')).toEqual([BANQUE]);
    expect(trierComptes([BANQUE, MOULINS, FOURNISSEURS, BANQUE]).map((c) => c.numero)).toEqual([
      '401',
      '401MOULINS',
      '512',
      '512',
    ]);
  });
});
