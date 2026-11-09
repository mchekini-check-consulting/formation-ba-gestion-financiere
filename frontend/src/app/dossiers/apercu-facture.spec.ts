import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { SessionService } from '../session/session.service';
import { ApercuFacture, MESSAGE_FACTURE_INTROUVABLE, affichage } from './apercu-facture';
import { VueFacture } from './facture.service';

const TICKET: VueFacture = { id: 5, nomFichier: 'ticket-fournitures.png', typeFichier: 'image/png', sens: 'ACHAT' };
const VENTE: VueFacture = { id: 6, nomFichier: 'facture-client-0042.pdf', typeFichier: 'application/pdf', sens: 'VENTE' };
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
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '42', factureId: '5' }) } },
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
});
