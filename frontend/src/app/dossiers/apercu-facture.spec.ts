import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { SessionService } from '../session/session.service';
import { ApercuFacture, MESSAGE_FACTURE_INTROUVABLE, MESSAGE_PLUS_DE_SUIVANTE, affichage } from './apercu-facture';
import { Lecture, VueFacture, valeurChamp } from './facture.service';

const TICKET: VueFacture = {
  id: 5,
  nomFichier: 'ticket-fournitures.png',
  typeFichier: 'image/png',
  sens: 'ACHAT',
  statut: 'RECUE',
  lecture: null,
  modification: null,
};
const VENTE: VueFacture = {
  id: 6,
  nomFichier: 'facture-client-0042.pdf',
  typeFichier: 'application/pdf',
  sens: 'VENTE',
  statut: 'RECUE',
  lecture: null,
  modification: null,
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

  describe('US-55 RG6 « Valider et passer à la suivante »', () => {
    const ligne = (id: number, statut: string) => ({ id, statut });

    it('ouvre la facture suivante de l’onglet qui n’est pas « Traitée »', async () => {
      const navigation = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      const { fixture } = await ouvrir({ ...TICKET, lecture: LECTURE });
      fixture.componentInstance.ouvrirLaSuivante({ ...TICKET, statut: 'TRAITEE' });
      http
        .expectOne('/api/dossiers/42/factures?sens=ACHAT')
        .flush({ factures: [ligne(9, 'TRAITEE'), ligne(5, 'TRAITEE'), ligne(7, 'RECUE')] });
      expect(navigation).toHaveBeenCalledWith(['/dossiers', '42', 'factures', 7]);
    });

    it('plus aucune facture à traiter ; service indisponible', async () => {
      const { fixture, page } = await ouvrir({ ...TICKET, lecture: LECTURE });
      fixture.componentInstance.ouvrirLaSuivante({ ...TICKET, statut: 'TRAITEE' });
      http.expectOne('/api/dossiers/42/factures?sens=ACHAT').flush({ factures: [ligne(5, 'TRAITEE')] });
      await fixture.whenStable();
      expect(page.querySelector('[role=status]')?.textContent).toBe(MESSAGE_PLUS_DE_SUIVANTE);

      fixture.componentInstance.ouvrirLaSuivante({ ...TICKET, statut: 'TRAITEE' });
      http.expectOne('/api/dossiers/42/factures?sens=ACHAT').flush(null, { status: 503, statusText: 'Unavailable' });
      await fixture.whenStable();
      expect(page.querySelector('[role=alert]')?.textContent).toContain('momentanément indisponible');
    });
  });

  describe('US-53 Lecture automatique', () => {
    function champs(page: HTMLElement) {
      return Array.from(page.querySelectorAll('.champ')).map((c) =>
        c.textContent?.replace(/\s+/g, ' ').replace(/\u00a0|\u202f/g, ' ').trim(),
      );
    }

    it('CA1 : les 9 champs lus ; RG4 « À vérifier »', async () => {
      const { page } = await ouvrir({ ...TICKET, lecture: LECTURE });
      expect(page.querySelector('h2')?.textContent).toBe('Informations de la facture');
      expect(champs(page)).toEqual([
        'Type de tiers Fournisseur',
        'Nom du tiers EDF',
        'Numéro de facture F-2026-0611',
        "Date d'émission 12/06/2026",
        'Montant HT 155,33 €',
        'Montant TVA 31,07 €',
        'Taux de TVA 20 %',
        'Montant TTC 186,40 €',
        'Compte de contrepartie 6061 – Fournitures non stockables À vérifier',
      ]);
      expect(page.querySelectorAll('.champ--doute')).toHaveLength(1);
    });

    it('RG3 : champs non lus « À renseigner »', async () => {
      const incomplete: Lecture = {
        ...LECTURE,
        typeTiers: null,
        nomTiers: null,
        dateEmission: null,
        tauxTva: null,
        montantTtc: null,
        compteContrepartie: null,
        aVerifier: [],
        aRenseigner: ['TYPE_TIERS', 'NOM_TIERS', 'DATE_EMISSION', 'TAUX_TVA', 'MONTANT_TTC', 'COMPTE_CONTREPARTIE'],
      };
      const { page } = await ouvrir({ ...TICKET, lecture: incomplete });
      expect(page.querySelectorAll('.marque--a-renseigner')).toHaveLength(6);
      expect(page.querySelector('.marque--a-verifier')).toBeNull();
    });

    it('lecture en cours', async () => {
      const { page } = await ouvrir({ ...TICKET, lecture: { ...LECTURE, statut: 'EN_COURS' } });
      expect(page.querySelector('.lecture')?.textContent).toContain('Lecture en cours…');
      expect(page.querySelector('.champs')).toBeNull();
    });

    it('CA4 : lecture échouée', async () => {
      const { page } = await ouvrir({ ...TICKET, lecture: { ...LECTURE, statut: 'ECHOUEE' } });
      expect(page.querySelector('.echec')?.textContent).toContain('Lecture échouée');
    });

    it('facture jamais lue : pas de bloc « Lecture automatique »', async () => {
      const { page } = await ouvrir();
      expect(page.querySelector('.lecture')).toBeNull();
    });

    it('US-54 CA1 : facture client lue', async () => {
      const client: Lecture = {
        ...LECTURE,
        typeTiers: 'CLIENT',
        nomTiers: 'Mairie de Lyon',
        compteContrepartie: '707',
        libelleContrepartie: 'Ventes de marchandises',
      };
      const { page } = await ouvrir({ ...VENTE, id: 5, lecture: client });
      expect(champs(page).slice(0, 2)).toEqual(['Type de tiers Client', 'Nom du tiers Mairie de Lyon']);
      expect(page.querySelector('.detail-tva')).toBeNull();
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
      expect(champs(page)).toContain('Taux de TVA Multiple');
      const lignesTva = Array.from(page.querySelectorAll('.detail-tva tbody tr')).map((tr) =>
        Array.from(tr.querySelectorAll('td')).map((td) => td.textContent?.replace(/\s/g, ' ').trim()),
      );
      expect(lignesTva).toEqual([
        ['20 %', '100,00 €', '20,00 €'],
        ['5,5 %', '—', '2,75 €'],
      ]);
    });

    it('compte sans libellé, facture client, montant non lu', () => {
      expect(valeurChamp({ ...LECTURE, libelleContrepartie: null }, 'COMPTE_CONTREPARTIE')).toBe('6061');
      expect(valeurChamp({ ...LECTURE, typeTiers: 'CLIENT' }, 'TYPE_TIERS')).toBe('Client');
      expect(valeurChamp({ ...LECTURE, montantHt: null }, 'MONTANT_HT')).toBeNull();
    });
  });
});
