import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { SessionService } from '../session/session.service';
import { Fiche, InformationsFacture, Lecture, VueFacture } from './facture.service';
import {
  FicheFacture,
  MESSAGE_A_COMPLETER,
  MESSAGE_ENREGISTREE,
  MESSAGE_PLUS_DE_SUIVANTE,
  MESSAGE_VALIDEE,
  depuisFormulaire,
  nombre,
  versFormulaire,
} from './fiche-facture';

// CA1 : facture EDF lue, TTC « À vérifier » à 186,00 €.
const EDF: InformationsFacture = {
  typeTiers: 'FOURNISSEUR',
  nomTiers: 'EDF',
  numeroFacture: 'F-2026-0611',
  dateEmission: '2026-06-12',
  montantHt: 155.33,
  montantTva: 31.07,
  tauxTva: 20,
  montantTtc: 186,
  compteContrepartie: '606',
};
const FICHE: Fiche = { statutTraitement: 'RECUE', motif: null, modifiable: true, informations: EDF };
const LECTURE: Lecture = {
  statut: 'LUE',
  ...EDF,
  tauxMultiple: false,
  detailTva: [],
  libelleContrepartie: null,
  aVerifier: ['MONTANT_TTC'],
  aRenseigner: [],
};
const FACTURE: VueFacture = {
  id: 5,
  nomFichier: 'edf.pdf',
  typeFichier: 'application/pdf',
  sens: 'ACHAT',
  lecture: LECTURE,
  fiche: FICHE,
};
const TRAITEE: VueFacture = {
  ...FACTURE,
  fiche: { ...FICHE, statutTraitement: 'TRAITEE', modifiable: false, informations: { ...EDF, montantTtc: 186.4 } },
};
const PLAN = '/api/dossiers/42/plan-comptable';
const VALIDATION = '/api/dossiers/42/factures/5/validation';
const INFORMATIONS = '/api/dossiers/42/factures/5/informations';

describe('US-55 Fiche facture', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [FicheFacture],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).ouvrir({ jeton: 'jeton', role: 'COLLABORATEUR', nomAffiche: 'Lucie Moreau' });
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  async function ouvrir(facture: VueFacture = FACTURE) {
    const fixture: ComponentFixture<FicheFacture> = TestBed.createComponent(FicheFacture);
    fixture.componentRef.setInput('dossierId', '42');
    fixture.componentRef.setInput('facture', facture);
    const misAJour: VueFacture[] = [];
    fixture.componentInstance.misAJour.subscribe((f) => misAJour.push(f));
    await fixture.whenStable();
    http.expectOne(PLAN).flush({
      comptes: [
        { numero: '606', libelle: 'Achats non stockés', sousCompte: false, actif: true },
        { numero: '613', libelle: 'Locations', sousCompte: false, actif: false },
      ],
      parDefaut: [],
    });
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    return { fixture, page, misAJour };
  }

  function saisir(page: HTMLElement, id: string, valeur: string) {
    const champ = page.querySelector<HTMLInputElement | HTMLSelectElement>(`#fiche-${id}`)!;
    champ.value = valeur;
    champ.dispatchEvent(new Event(champ instanceof HTMLSelectElement ? 'change' : 'input'));
  }

  function bouton(page: HTMLElement, texte: string) {
    return Array.from(page.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes(texte),
    )!;
  }

  it('RG1, RG2 : les 9 champs modifiables, TTC « À vérifier » ; RG3 : comptes actifs du plan', async () => {
    const { fixture, page } = await ouvrir();
    expect(page.querySelector('h2')?.textContent).toBe('Informations de la facture');
    expect(page.textContent).toContain('Statut : Reçue');
    expect(page.querySelectorAll('.champ')).toHaveLength(9);
    expect(page.querySelector('.champ--a-verifier label')?.textContent).toContain('Montant TTC');
    expect(page.querySelector('.marque--a-renseigner')).toBeNull();
    const options = Array.from(page.querySelectorAll('#fiche-COMPTE_CONTREPARTIE option')).map((o) =>
      o.textContent?.trim(),
    );
    expect(options).toEqual(['—', '606 – Achats non stockés']);

    // Champ vidé : « À renseigner ».
    saisir(page, 'NUMERO_FACTURE', '');
    await fixture.whenStable();
    expect(page.querySelector('.champ--a-renseigner label')?.textContent).toContain('Numéro de facture');
  });

  it('CA1 : TTC corrigé à 186,40 € puis « Valider » : facture « Traitée »', async () => {
    const { fixture, page, misAJour } = await ouvrir();
    saisir(page, 'MONTANT_TTC', '186,40');
    bouton(page, 'Valider').click();
    await fixture.whenStable();

    const validation = http.expectOne(VALIDATION);
    expect(validation.request.method).toBe('POST');
    expect(validation.request.body).toEqual({ ...EDF, montantTtc: 186.4 });
    validation.flush({ facture: TRAITEE, suivante: 6 });
    fixture.componentRef.setInput('facture', misAJour[0]);
    await fixture.whenStable();

    expect(misAJour).toEqual([TRAITEE]);
    expect(page.querySelector('[role=status].succes')?.textContent).toBe(MESSAGE_VALIDEE);
    // RG8 : lecture seule, plus aucun bouton.
    expect(page.querySelector<HTMLInputElement>('#fiche-MONTANT_TTC')?.disabled).toBe(true);
    expect(page.querySelector<HTMLInputElement>('#fiche-MONTANT_TTC')?.value).toBe('186,40');
    expect(page.querySelector('.actions')).toBeNull();
    expect(page.textContent).toContain('Facture traitée : ses informations ne sont plus modifiables.');
  });

  it('RG6 : « Valider et passer à la suivante » ouvre la facture suivante, ou le dit s’il n’y en a plus', async () => {
    const naviguer = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const { fixture, page } = await ouvrir();

    bouton(page, 'Valider et passer à la suivante').click();
    http.expectOne(VALIDATION).flush({ facture: TRAITEE, suivante: 6 });
    await fixture.whenStable();
    expect(naviguer).toHaveBeenCalledWith(['/dossiers', '42', 'factures', 6]);

    bouton(page, 'Valider et passer à la suivante').click();
    http.expectOne(VALIDATION).flush({ facture: TRAITEE, suivante: null });
    await fixture.whenStable();
    expect(naviguer).toHaveBeenCalledTimes(1);
    expect(page.querySelector('.succes')?.textContent).toBe(MESSAGE_PLUS_DE_SUIVANTE);
  });

  it('CA2, CA3 : refus du backend affiché tel quel ; la saisie reste à l’écran', async () => {
    const { fixture, page, misAJour } = await ouvrir();
    saisir(page, 'NUMERO_FACTURE', '');
    bouton(page, 'Valider').click();
    const validation = http.expectOne(VALIDATION);
    expect(validation.request.body.numeroFacture).toBeNull();
    validation.flush({ message: 'Indiquez le numéro de facture' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Indiquez le numéro de facture');
    expect(misAJour).toEqual([]);

    saisir(page, 'NUMERO_FACTURE', 'F-2026-0611');
    saisir(page, 'MONTANT_TTC', '190');
    bouton(page, 'Valider').click();
    http
      .expectOne(VALIDATION)
      .flush({ message: 'Montants incohérents : HT + TVA ≠ TTC' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Montants incohérents : HT + TVA ≠ TTC');
    expect(page.querySelector<HTMLInputElement>('#fiche-MONTANT_TTC')?.value).toBe('190');
  });

  it('RG2 : « Enregistrer » garde les corrections sans valider', async () => {
    const { fixture, page, misAJour } = await ouvrir();
    saisir(page, 'NOM_TIERS', 'EDF Entreprises');
    saisir(page, 'COMPTE_CONTREPARTIE', '');
    bouton(page, 'Enregistrer').click();
    const enregistrement = http.expectOne(INFORMATIONS);
    expect(enregistrement.request.method).toBe('PUT');
    expect(enregistrement.request.body).toEqual({ ...EDF, nomTiers: 'EDF Entreprises', compteContrepartie: null });
    const enregistree = { ...FACTURE, fiche: { ...FICHE, informations: { ...EDF, nomTiers: 'EDF Entreprises' } } };
    enregistrement.flush({ facture: enregistree, suivante: null });
    await fixture.whenStable();
    expect(misAJour).toEqual([enregistree]);
    expect(page.querySelector('.succes')?.textContent).toBe(MESSAGE_ENREGISTREE);
  });

  it('nombre mal saisi : message, rien n’est envoyé', async () => {
    const { fixture, page } = await ouvrir();
    saisir(page, 'MONTANT_HT', 'cent');
    bouton(page, 'Enregistrer').click();
    await fixture.whenStable();
    http.expectNone(INFORMATIONS);
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Montant HT : nombre invalide');
  });

  it('RG8 (409), facture introuvable (404), service indisponible', async () => {
    const { fixture, page } = await ouvrir();
    const cas: [number, string][] = [
      [409, 'Cette facture n’est plus modifiable'],
      [404, 'Facture introuvable ou accès non autorisé'],
      [503, 'Le service est momentanément indisponible. Réessayez.'],
    ];
    for (const [status, message] of cas) {
      bouton(page, 'Enregistrer').click();
      http
        .expectOne(INFORMATIONS)
        .flush(status === 409 ? { message } : null, { status, statusText: 'Refus' });
      await fixture.whenStable();
      expect(page.querySelector('[role=alert]')?.textContent).toBe(message);
    }
  });

  it('CA5 : passer la facture en « À compléter » avec un motif, visible du client', async () => {
    const { fixture, page, misAJour } = await ouvrir();
    bouton(page, 'Passer en « À compléter »').click();
    await fixture.whenStable();
    bouton(page, 'Confirmer').click();
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Indiquez le motif');
    http.expectNone('/api/dossiers/42/factures/5/statut');

    bouton(page, 'Annuler').click();
    await fixture.whenStable();
    bouton(page, 'Passer en « À compléter »').click();
    await fixture.whenStable();
    saisir(page, 'motif', '  Numéro de facture manquant ');
    bouton(page, 'Confirmer').click();
    const statut = http.expectOne('/api/dossiers/42/factures/5/statut');
    expect(statut.request.method).toBe('PATCH');
    expect(statut.request.body).toEqual({ statut: 'A_COMPLETER', motif: 'Numéro de facture manquant' });
    statut.flush({ id: 5, statut: 'A_COMPLETER', motif: 'Numéro de facture manquant' });
    await fixture.whenStable();
    expect(misAJour[0].fiche).toEqual({ ...FICHE, statutTraitement: 'A_COMPLETER', motif: 'Numéro de facture manquant' });
    expect(page.querySelector('.succes')?.textContent).toBe(MESSAGE_A_COMPLETER);

    fixture.componentRef.setInput('facture', misAJour[0]);
    await fixture.whenStable();
    expect(page.textContent).toContain('Motif « À compléter » : Numéro de facture manquant');
    // US-51 RG2 : depuis « À compléter », pas de nouveau passage en « À compléter ».
    expect(bouton(page, 'Passer en « À compléter »')).toBeUndefined();
  });

  it('« À compléter » refusé par le backend', async () => {
    const { fixture, page } = await ouvrir();
    bouton(page, 'Passer en « À compléter »').click();
    await fixture.whenStable();
    saisir(page, 'motif', 'Numéro manquant');
    bouton(page, 'Confirmer').click();
    http
      .expectOne('/api/dossiers/42/factures/5/statut')
      .flush({ message: 'Ce changement de statut n\'est pas autorisé' }, { status: 409, statusText: 'Conflit' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Ce changement de statut n\'est pas autorisé');
  });

  it('RG3 : contrepartie lue hors du plan signalée ; plan comptable indisponible', async () => {
    const fixture = TestBed.createComponent(FicheFacture);
    fixture.componentRef.setInput('dossierId', '42');
    fixture.componentRef.setInput('facture', {
      ...FACTURE,
      fiche: { ...FICHE, informations: { ...EDF, compteContrepartie: '6061' } },
    });
    await fixture.whenStable();
    http.expectOne(PLAN).flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Plan comptable indisponible');
    expect(page.querySelector('#fiche-COMPTE_CONTREPARTIE')?.textContent).toContain('6061 (absent du plan comptable)');
  });

  it('US-54 CA2 : plusieurs taux, le taux n’est ni saisi ni « À renseigner »', async () => {
    const { page } = await ouvrir({
      ...FACTURE,
      lecture: { ...LECTURE, tauxMultiple: true, aVerifier: [] },
      fiche: { ...FICHE, informations: { ...EDF, tauxTva: null } },
    });
    expect(page.querySelector<HTMLInputElement>('#fiche-TAUX_TVA')?.value).toBe('Multiple');
    expect(page.querySelector('.marque--a-renseigner')).toBeNull();
  });

  it('conversions du formulaire', () => {
    expect(nombre('1 250,50 €')).toBe(1250.5);
    expect(nombre(' ')).toBeNull();
    expect(nombre('-12.5')).toBe(-12.5);
    expect(nombre('12,5,3')).toBeUndefined();
    expect(versFormulaire({ ...EDF, tauxTva: 5.5, montantHt: null })).toEqual({
      typeTiers: 'FOURNISSEUR',
      nomTiers: 'EDF',
      numeroFacture: 'F-2026-0611',
      dateEmission: '2026-06-12',
      montantHt: '',
      montantTva: '31,07',
      tauxTva: '5,5',
      montantTtc: '186,00',
      compteContrepartie: '606',
    });
    const vide = versFormulaire({
      typeTiers: null,
      nomTiers: null,
      numeroFacture: null,
      dateEmission: null,
      montantHt: null,
      montantTva: null,
      tauxTva: null,
      montantTtc: null,
      compteContrepartie: null,
    });
    expect(depuisFormulaire({ ...vide, nomTiers: '  ', typeTiers: 'AUTRE' })).toEqual({
      typeTiers: null,
      nomTiers: null,
      numeroFacture: null,
      dateEmission: null,
      montantHt: null,
      montantTva: null,
      tauxTva: null,
      montantTtc: null,
      compteContrepartie: null,
    });
    expect(depuisFormulaire({ ...vide, tauxTva: 'vingt' })).toBe('Taux de TVA : nombre invalide');
    expect(depuisFormulaire({ ...vide, montantTtc: 'x' })).toBe('Montant TTC : nombre invalide');
  });
});
