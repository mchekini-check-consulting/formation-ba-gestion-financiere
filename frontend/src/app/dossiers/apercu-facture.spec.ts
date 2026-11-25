import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { SessionService } from '../session/session.service';
import { ApercuFacture, MESSAGE_FACTURE_INTROUVABLE, affichage } from './apercu-facture';
import { Fiche, Lecture, VueFacture, valeurChamp } from './facture.service';

const FICHE_VIDE: Fiche = {
  statutTraitement: 'RECUE',
  motif: null,
  modifiable: true,
  informations: {
    typeTiers: null,
    nomTiers: null,
    numeroFacture: null,
    dateEmission: null,
    montantHt: null,
    montantTva: null,
    tauxTva: null,
    montantTtc: null,
    compteContrepartie: null,
  },
};

const TICKET: VueFacture = {
  id: 5,
  nomFichier: 'ticket-fournitures.png',
  typeFichier: 'image/png',
  sens: 'ACHAT',
  lecture: null,
  fiche: FICHE_VIDE,
};
const VENTE: VueFacture = {
  id: 6,
  nomFichier: 'facture-client-0042.pdf',
  typeFichier: 'application/pdf',
  sens: 'VENTE',
  lecture: null,
  fiche: FICHE_VIDE,
};
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
  montantTtc: 186.4,
  compteContrepartie: '6061',
  libelleContrepartie: 'Fournitures non stockables',
  aVerifier: ['COMPTE_CONTREPARTIE'],
  aRenseigner: [],
};
const FICHIER = '/api/dossiers/42/factures/5/fichier';
const PLAN = '/api/dossiers/42/plan-comptable';
const PLAN_DOSSIER = {
  comptes: [{ numero: '606', libelle: 'Achats non stockés', sousCompte: false, actif: true }],
  parDefaut: [],
};

describe('US-13 Aperçu et téléchargement d’une facture', () => {
  let http: HttpTestingController;
  let creer: ReturnType<typeof vi.spyOn>;
  let liberer: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    sessionStorage.clear();
    creer = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:apercu');
    liberer = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    await TestBed.configureTestingModule({
      imports: [ApercuFacture],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ id: '42', factureId: '5' })) },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).ouvrir({ jeton: 'jeton', role: 'COLLABORATEUR', nomAffiche: 'Jean Dupont' });
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  async function ouvrir(facture: VueFacture = TICKET) {
    const fixture = TestBed.createComponent(ApercuFacture);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Chargement…');
    http.expectOne('/api/dossiers/42/factures/5').flush(facture);
    await fixture.whenStable();
    expect(page.textContent).toContain("Chargement de l'aperçu…");
    const fichier = http.expectOne(FICHIER);
    expect(fichier.request.responseType).toBe('blob');
    fichier.flush(new Blob(['x'], { type: facture.typeFichier }));
    // US-55 : la fiche charge le plan comptable du dossier (RG3).
    http.expectOne(PLAN).flush(PLAN_DOSSIER);
    await fixture.whenStable();
    return { fixture, page };
  }

  it('RG1 : aperçu d’une image', async () => {
    const { page } = await ouvrir();
    expect(page.querySelector('h1')?.textContent).toBe('ticket-fournitures.png');
    expect(page.querySelector('img')?.getAttribute('src')).toBe('blob:apercu');
    expect(page.querySelector('img')?.getAttribute('alt')).toBe('Aperçu de ticket-fournitures.png');
    expect(page.querySelector('iframe')).toBeNull();
    expect(page.querySelector('.retour')?.getAttribute('href')).toBe('/dossiers/42?onglet=fournisseurs');
  });

  it('RG1 : aperçu d’un PDF, retour sur l’onglet Factures clients', async () => {
    const { page } = await ouvrir(VENTE);
    expect(page.querySelector('iframe')?.getAttribute('title')).toBe('Aperçu de facture-client-0042.pdf');
    expect(page.querySelector('.retour')?.getAttribute('href')).toBe('/dossiers/42?onglet=clients');
  });

  it('autre type de fichier : pas d’aperçu, téléchargement possible', async () => {
    const { page } = await ouvrir({ ...TICKET, typeFichier: 'application/zip' });
    expect(page.textContent).toContain('Aperçu indisponible pour ce type de fichier');
    expect(affichage('image/jpeg')).toBe('image');
    expect(affichage('application/pdf')).toBe('pdf');
  });

  it('RG2 : « Télécharger » enregistre le fichier d’origine sous son nom', async () => {
    const { fixture, page } = await ouvrir();
    const clic = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    const bouton = page.querySelector<HTMLButtonElement>('.bouton');
    expect(bouton?.textContent?.trim()).toBe('Télécharger');

    bouton?.click();
    await fixture.whenStable();
    expect(bouton?.disabled).toBe(true);
    http.expectOne(`${FICHIER}?telechargement=true`).flush(new Blob(['x'], { type: 'image/png' }));
    await fixture.whenStable();
    expect(bouton?.disabled).toBe(false);
    expect(clic).toHaveBeenCalledTimes(1);
    expect((clic.mock.instances[0] as unknown as HTMLAnchorElement).download).toBe('ticket-fournitures.png');
    expect(creer).toHaveBeenCalledTimes(2);
    expect(liberer).toHaveBeenCalledTimes(1);

    fixture.destroy();
    expect(liberer).toHaveBeenCalledWith('blob:apercu');
  });

  it('RG3 : facture d’un dossier sans droits ou adresse tapée à la main', async () => {
    for (const status of [404, 403, 400]) {
      const fixture = TestBed.createComponent(ApercuFacture);
      await fixture.whenStable();
      http.expectOne('/api/dossiers/42/factures/5').flush({ message: MESSAGE_FACTURE_INTROUVABLE }, { status, statusText: 'Refus' });
      await fixture.whenStable();
      const page = fixture.nativeElement as HTMLElement;
      expect(page.querySelector('[role=alert]')?.textContent).toBe('Facture introuvable ou accès non autorisé');
      expect(page.querySelector('img, iframe')).toBeNull();
    }
  });

  it('service indisponible (aperçu ou téléchargement)', async () => {
    const fixture = TestBed.createComponent(ApercuFacture);
    await fixture.whenStable();
    http.expectOne('/api/dossiers/42/factures/5').flush(TICKET);
    await fixture.whenStable();
    http.expectOne(FICHIER).flush(null, { status: 503, statusText: 'Unavailable' });
    http.match(PLAN).forEach((plan) => plan.flush(PLAN_DOSSIER));
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent).toContain(
      'momentanément indisponible',
    );

    const autre = await ouvrir();
    autre.page.querySelector<HTMLButtonElement>('.bouton')?.click();
    await autre.fixture.whenStable();
    http.expectOne(`${FICHIER}?telechargement=true`).flush(null, { status: 503, statusText: 'Unavailable' });
    await autre.fixture.whenStable();
    expect(autre.page.querySelector('[role=alert]')?.textContent).toContain('momentanément indisponible');
  });

  it('« Télécharger » sans facture chargée : rien ne part', () => {
    const fixture = TestBed.createComponent(ApercuFacture);
    fixture.componentInstance.telecharger();
    http.expectNone(`${FICHIER}?telechargement=true`);
    expect(creer).not.toHaveBeenCalled();
  });

  describe('US-53 Lecture automatique et US-55 fiche facture', () => {
    function valeurs(page: HTMLElement) {
      return Array.from(page.querySelectorAll<HTMLInputElement | HTMLSelectElement>('.champ input, .champ select')).map(
        (champ) => champ.value,
      );
    }

    it('US-53 CA1, US-55 RG1 : les 9 champs lus dans la fiche, à côté du document ; RG2 « À vérifier »', async () => {
      const { page } = await ouvrir({
        ...TICKET,
        lecture: LECTURE,
        fiche: {
          ...FICHE_VIDE,
          informations: {
            typeTiers: 'FOURNISSEUR',
            nomTiers: 'EDF',
            numeroFacture: 'F-2026-0611',
            dateEmission: '2026-06-12',
            montantHt: 155.33,
            montantTva: 31.07,
            tauxTva: 20,
            montantTtc: 186.4,
            compteContrepartie: '6061',
          },
        },
      });
      expect(page.querySelector('.fiche-facture .apercu img')).not.toBeNull();
      expect(page.querySelector('.lecture h2')?.textContent).toBe('Lecture automatique');
      expect(valeurs(page)).toEqual([
        'FOURNISSEUR',
        'EDF',
        'F-2026-0611',
        '2026-06-12',
        '155,33',
        '31,07',
        '20',
        '186,40',
        '6061',
      ]);
      expect(page.querySelectorAll('.champ--a-verifier')).toHaveLength(1);
      // RG3 : contrepartie lue hors du plan comptable du dossier, signalée.
      expect(page.querySelector('#fiche-COMPTE_CONTREPARTIE')?.textContent).toContain(
        '6061 (absent du plan comptable)',
      );
    });

    it('lecture en cours : fiche en attente, non modifiable', async () => {
      const { page } = await ouvrir({
        ...TICKET,
        lecture: { ...LECTURE, statut: 'EN_COURS' },
        fiche: { ...FICHE_VIDE, modifiable: false },
      });
      expect(page.querySelector('.lecture')?.textContent).toContain('Lecture en cours…');
      expect(page.querySelector<HTMLInputElement>('#fiche-NOM_TIERS')?.disabled).toBe(true);
      expect(page.querySelector('.actions')).toBeNull();
    });

    it('US-53 CA4, US-55 RG7 : lecture échouée, champs à saisir dans la même fiche', async () => {
      const { page } = await ouvrir({ ...TICKET, lecture: { ...LECTURE, statut: 'ECHOUEE', aVerifier: [] } });
      expect(page.querySelector('.echec')?.textContent).toContain('Lecture échouée');
      expect(page.querySelectorAll('.marque--a-renseigner')).toHaveLength(9);
      expect(page.querySelector<HTMLInputElement>('#fiche-NOM_TIERS')?.disabled).toBe(false);
    });

    it('facture lue : invitation à vérifier ; jamais lue : pas de bloc « Lecture automatique »', async () => {
      const lue = await ouvrir({ ...TICKET, lecture: LECTURE });
      expect(lue.page.querySelector('.lecture')?.textContent).toContain('vérifiez les champs signalés');
      const { page } = await ouvrir();
      expect(page.querySelector('.lecture')).toBeNull();
      expect(page.querySelector('app-fiche-facture')).not.toBeNull();
    });

    it('US-54 CA2 : plusieurs taux de TVA, « Multiple » et détail par taux', async () => {
      const multiple: Lecture = {
        ...LECTURE,
        tauxTva: null,
        tauxMultiple: true,
        detailTva: [
          { taux: 20, baseHt: 100, montantTva: 20 },
          { taux: 5.5, baseHt: null, montantTva: 2.75 },
        ],
      };
      const { page } = await ouvrir({ ...TICKET, lecture: multiple });
      expect(page.querySelector<HTMLInputElement>('#fiche-TAUX_TVA')?.value).toBe('Multiple');
      const lignesTva = Array.from(page.querySelectorAll('.detail-tva tbody tr')).map((tr) =>
        Array.from(tr.querySelectorAll('td')).map((td) => td.textContent?.replace(/\s/g, ' ').trim()),
      );
      expect(lignesTva).toEqual([
        ['20 %', '100,00 €', '20,00 €'],
        ['5,5 %', '—', '2,75 €'],
      ]);
    });

    it('US-55 : la fiche mise à jour remplace celle de la page', async () => {
      const { fixture, page } = await ouvrir();
      (fixture.componentInstance as unknown as { ficheMiseAJour(f: VueFacture): void }).ficheMiseAJour({
        ...TICKET,
        fiche: { ...FICHE_VIDE, statutTraitement: 'TRAITEE', modifiable: false },
      });
      await fixture.whenStable();
      expect(page.textContent).toContain('Statut : Traitée');
      expect(page.textContent).toContain('Facture traitée : ses informations ne sont plus modifiables.');
    });

    it('compte sans libellé, facture client, montant non lu', () => {
      expect(valeurChamp({ ...LECTURE, libelleContrepartie: null }, 'COMPTE_CONTREPARTIE')).toBe('6061');
      expect(valeurChamp({ ...LECTURE, typeTiers: 'CLIENT' }, 'TYPE_TIERS')).toBe('Client');
      expect(valeurChamp({ ...LECTURE, montantHt: null }, 'MONTANT_HT')).toBeNull();
      expect(valeurChamp(LECTURE, 'COMPTE_CONTREPARTIE')).toBe('6061 – Fournitures non stockables');
      expect(valeurChamp({ ...LECTURE, tauxMultiple: true }, 'TAUX_TVA')).toBe('Multiple');
      expect(valeurChamp(LECTURE, 'DATE_EMISSION')).toBe('12/06/2026');
    });
  });
});
