import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionService } from '../session/session.service';
import { Lecture, VueFacture } from './facture.service';
import { FicheInformations, informationsDe, nombre, saisieDe } from './fiche-informations';

const LECTURE: Lecture = {
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
  montantTtc: 186,
  compteContrepartie: '606',
  libelleContrepartie: 'Achats non stockés de matières et fournitures',
  aVerifier: ['MONTANT_TTC'],
  aRenseigner: [],
};
const EDF: VueFacture = {
  id: 5,
  nomFichier: 'edf.pdf',
  typeFichier: 'application/pdf',
  sens: 'ACHAT',
  statut: 'RECUE',
  lecture: LECTURE,
  modification: null,
  ecriture: null,
};
const PLAN = {
  comptes: [
    { numero: '606', libelle: 'Achats non stockés de matières et fournitures', sousCompte: false, actif: true },
    { numero: '607', libelle: 'Achats de marchandises', sousCompte: false, actif: false },
    { numero: '626', libelle: 'Frais postaux et de télécommunications', sousCompte: false, actif: true },
  ],
  parDefaut: [],
};
const INFOS = '/api/dossiers/42/factures/5/informations';
const VALIDATION = '/api/dossiers/42/factures/5/validation';

describe('US-55 Vérifier, corriger et valider les informations d’une facture', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [FicheInformations],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).ouvrir({ jeton: 'jeton', role: 'COLLABORATEUR', nomAffiche: 'Lucie Bernard' });
  });

  afterEach(() => http.verify());

  async function afficher(facture: VueFacture = EDF) {
    const fixture: ComponentFixture<FicheInformations> = TestBed.createComponent(FicheInformations);
    fixture.componentRef.setInput('dossierId', '42');
    fixture.componentRef.setInput('facture', facture);
    const modifiees: VueFacture[] = [];
    const validees: VueFacture[] = [];
    fixture.componentInstance.modifiee.subscribe((v) => modifiees.push(v));
    fixture.componentInstance.validee.subscribe((v) => validees.push(v));
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement, modifiees, validees };
  }

  /** RG2 : clic sur le champ « Montant TTC » (« À vérifier ») : fiche en modification. */
  async function modifierTtc(fixture: ComponentFixture<FicheInformations>, page: HTMLElement, planACharger = true) {
    page.querySelector<HTMLButtonElement>('[aria-label="Modifier : Montant TTC"]')?.click();
    await fixture.whenStable();
    if (planACharger) {
      http.expectOne('/api/dossiers/42/plan-comptable').flush(PLAN);
    }
    await new Promise((fin) => setTimeout(fin));
    await fixture.whenStable();
  }

  async function saisir(fixture: ComponentFixture<FicheInformations>, page: HTMLElement, id: string, texte: string) {
    const champ = page.querySelector<HTMLInputElement>(`#${id}`)!;
    champ.value = texte;
    champ.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  it('RG2 : un clic sur un champ met la fiche en modification, le curseur sur ce champ ; RG3 : comptes actifs', async () => {
    const { fixture, page } = await afficher();
    expect(page.querySelector('form')).toBeNull();
    await modifierTtc(fixture, page);
    expect(page.querySelector('form')).not.toBeNull();
    expect(document.activeElement?.id).toBe('champ-MONTANT_TTC');
    expect(page.querySelector<HTMLInputElement>('#champ-MONTANT_TTC')?.value).toBe('186');
    expect(page.querySelector<HTMLInputElement>('#champ-MONTANT_HT')?.value).toBe('155,33');
    const comptes = Array.from(page.querySelectorAll('#champ-COMPTE_CONTREPARTIE option')).map((o) => o.textContent?.trim());
    expect(comptes).toEqual(['—', '606 – Achats non stockés de matières et fournitures', '626 – Frais postaux et de télécommunications']);

    page.querySelector<HTMLButtonElement>('.lien')?.click();
    await fixture.whenStable();
    expect(page.querySelector('form')).toBeNull();
  });

  it('« Enregistrer » : informations envoyées (virgule acceptée), fiche à jour', async () => {
    const { fixture, page, modifiees, validees } = await afficher();
    await modifierTtc(fixture, page);
    await saisir(fixture, page, 'champ-MONTANT_TTC', '186,40 €');
    page.querySelector<HTMLButtonElement>('button[type=submit]')?.click();
    await fixture.whenStable();
    const envoi = http.expectOne(INFOS);
    expect(envoi.request.method).toBe('PUT');
    expect(envoi.request.body).toEqual({
      typeTiers: 'FOURNISSEUR',
      nomTiers: 'EDF',
      numeroFacture: 'F-2026-0611',
      dateEmission: '2026-06-12',
      montantHt: 155.33,
      montantTva: 31.07,
      tauxTva: 20,
      montantTtc: 186.4,
      compteContrepartie: '606',
    });
    const corrigee = { ...EDF, lecture: { ...LECTURE, montantTtc: 186.4, aVerifier: [] } };
    envoi.flush(corrigee);
    await fixture.whenStable();
    expect(modifiees).toEqual([corrigee]);
    expect(validees).toEqual([]);
    expect(page.querySelector('form')).toBeNull();
  });

  it('CA1 : « Valider » ; « Valider et passer à la suivante »', async () => {
    const { fixture, page, modifiees, validees } = await afficher();
    await modifierTtc(fixture, page);
    const boutons = Array.from(page.querySelectorAll<HTMLButtonElement>('.actions button'));
    boutons.find((b) => b.textContent?.trim() === 'Valider')?.click();
    await fixture.whenStable();
    const traitee: VueFacture = { ...EDF, statut: 'TRAITEE' };
    const validation = http.expectOne(VALIDATION);
    expect(validation.request.method).toBe('POST');
    validation.flush(traitee);
    await fixture.whenStable();
    expect(modifiees).toEqual([traitee]);
    expect(validees).toEqual([]);

    // Plan comptable déjà chargé : pas de second appel.
    await modifierTtc(fixture, page, false);
    Array.from(page.querySelectorAll<HTMLButtonElement>('.actions button'))
      .find((b) => b.textContent?.includes('suivante'))
      ?.click();
    await fixture.whenStable();
    http.expectOne(VALIDATION).flush(traitee);
    await fixture.whenStable();
    expect(validees).toEqual([traitee]);
  });

  it('CA2, CA3 : refus du backend affiché, la fiche reste en modification', async () => {
    const { fixture, page, modifiees } = await afficher();
    await modifierTtc(fixture, page);
    Array.from(page.querySelectorAll<HTMLButtonElement>('.actions button'))
      .find((b) => b.textContent?.trim() === 'Valider')
      ?.click();
    await fixture.whenStable();
    http
      .expectOne(VALIDATION)
      .flush({ message: 'Montants incohérents : HT + TVA ≠ TTC' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Montants incohérents : HT + TVA ≠ TTC');
    expect(page.querySelector('form')).not.toBeNull();
    expect(modifiees).toEqual([]);

    page.querySelector<HTMLButtonElement>('button[type=submit]')?.click();
    await fixture.whenStable();
    http.expectOne(INFOS).flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('momentanément indisponible');
  });

  it('montant mal saisi : message, rien n’est envoyé', async () => {
    const { fixture, page } = await afficher();
    await modifierTtc(fixture, page);
    await saisir(fixture, page, 'champ-MONTANT_HT', 'cent euros');
    page.querySelector<HTMLButtonElement>('button[type=submit]')?.click();
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Montant HT : nombre attendu');
  });

  it('facture « Traitée » dont l’écriture est exportée : lecture seule ; RG8 dernière modification', async () => {
    const { page } = await afficher({
      ...EDF,
      sens: 'VENTE',
      statut: 'TRAITEE',
      modification: { par: 'Lucie Bernard', le: '2026-11-25T09:00:00Z' },
      ecriture: {
        journal: 'VT',
        date: '2026-06-12',
        libelle: 'Café Lumière F-2026-014',
        genereeLe: '2026-11-25T09:00:00Z',
        genereePar: 'Lucie Bernard',
        modifieeLe: null,
        modifieePar: null,
        exportee: true,
        aJour: true,
        lignes: [],
        totalDebit: 0,
        totalCredit: 0,
      },
    });
    expect(page.querySelector('.traitee')?.textContent).toBe('Traitée');
    expect(page.querySelector('.valeur')).toBeNull();
    expect(page.querySelector('.modification')?.textContent).toBe('Modifiée par Lucie Bernard le 25/11/2026 à 10:00');
  });

  it('US-57 RG5 : facture de vente « Traitée », écriture non exportée : « Corriger »', async () => {
    const { page } = await afficher({ ...EDF, sens: 'VENTE', statut: 'TRAITEE' });
    expect(Array.from(page.querySelectorAll('button')).some((b) => b.textContent?.trim() === 'Corriger')).toBe(true);
  });

  it('US-56 RG5 : facture d’achat « Traitée », écriture non exportée : « Corriger », sans « Valider »', async () => {
    const { fixture, page } = await afficher({ ...EDF, statut: 'TRAITEE' });
    const corriger = Array.from(page.querySelectorAll<HTMLButtonElement>('button')).find(
      (b) => b.textContent?.trim() === 'Corriger',
    );
    expect(corriger).toBeDefined();
    await modifierTtc(fixture, page);
    const boutons = Array.from(page.querySelectorAll('.actions button')).map((b) => b.textContent?.trim());
    expect(boutons).toEqual(['Enregistrer', 'Annuler']);
  });

  it('CA4, RG7 : lecture échouée, champs à saisir ; lecture en cours : pas de modification', async () => {
    const echouee: Lecture = {
      ...saisieVide(),
      statut: 'ECHOUEE',
    };
    const { page } = await afficher({ ...EDF, lecture: echouee });
    expect(page.querySelector('.echec')?.textContent).toContain('Lecture échouée');
    expect(page.querySelectorAll('button.valeur .marque--a-renseigner')).toHaveLength(9);

    const enCours = await afficher({ ...EDF, lecture: { ...LECTURE, statut: 'EN_COURS' } });
    expect(enCours.page.querySelector('.champs')).toBeNull();
  });

  it('plan comptable indisponible ; compte saisi absent de la liste gardé', async () => {
    const { fixture, page } = await afficher({ ...EDF, lecture: { ...LECTURE, compteContrepartie: '607' } });
    page.querySelector<HTMLButtonElement>('.champs + .bouton')?.click();
    await fixture.whenStable();
    http.expectOne('/api/dossiers/42/plan-comptable').flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('momentanément indisponible');
    const options = Array.from(page.querySelectorAll('#champ-COMPTE_CONTREPARTIE option')).map((o) => o.textContent?.trim());
    expect(options).toEqual(['—', '607']);
  });

  it('conversions du formulaire', () => {
    expect(nombre('1 186,40 €', 'Montant TTC')).toBe(1186.4);
    expect(nombre('5,5 %', 'Taux de TVA')).toBe(5.5);
    expect(nombre('  ', 'Montant HT')).toBeNull();
    expect(nombre('12,3,4', 'Montant HT')).toBe('Montant HT : nombre attendu');
    const vide = saisieDe(saisieVide());
    expect(vide.montantHt).toBe('');
    expect(informationsDe({ ...vide, nomTiers: '  ', typeTiers: 'CLIENT' })).toEqual({
      typeTiers: 'CLIENT',
      nomTiers: null,
      numeroFacture: null,
      dateEmission: null,
      montantHt: null,
      montantTva: null,
      tauxTva: null,
      montantTtc: null,
      compteContrepartie: null,
    });
  });
});

function saisieVide(): Lecture {
  return {
    ...LECTURE,
    typeTiers: null,
    nomTiers: null,
    numeroFacture: null,
    dateEmission: null,
    montantHt: null,
    montantTva: null,
    tauxTva: null,
    montantTtc: null,
    compteContrepartie: null,
    libelleContrepartie: null,
    aVerifier: [],
  };
}
