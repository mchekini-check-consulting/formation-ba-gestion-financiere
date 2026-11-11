import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionService } from '../session/session.service';
import { EspaceClient, MESSAGE_SUPPRIMEE, MESSAGE_TROP_DE_FICHIERS, resumeDepot } from './espace-client';
import { FactureDeposee, MESSAGE_FORMAT, MESSAGE_TAILLE, TAILLE_MAX, refusAvantEnvoi } from './espace-client.service';

const URL = '/api/espace-client/factures';
const INDISPONIBLE = 'Le service est momentanément indisponible. Réessayez.';

const TICKET: FactureDeposee = {
  id: 7,
  nomFichier: 'ticket-carburant.jpg',
  deposeeLe: '2026-11-08T09:05:00Z',
  sens: 'ACHAT',
  supprimable: true,
};
const LOYER: FactureDeposee = { ...TICKET, id: 8, nomFichier: 'loyer.pdf', supprimable: false };

function fichier(nom: string, type: string, taille = 10): File {
  return new File([new Uint8Array(taille)], nom, { type });
}

describe('Espace client : règles de dépôt', () => {
  it('US-15 RG1 : PDF, JPG ou PNG, 10 Mo maximum', () => {
    expect(refusAvantEnvoi(fichier('facture.pdf', 'application/pdf'))).toBeNull();
    expect(refusAvantEnvoi(fichier('photo.JPG', ''))).toBeNull();
    expect(refusAvantEnvoi(fichier('scan', 'image/png'))).toBeNull();
    expect(refusAvantEnvoi(fichier('releve.xlsx', 'application/vnd.ms-excel'))).toBe(MESSAGE_FORMAT);
    expect(refusAvantEnvoi(fichier('gros.pdf', 'application/pdf', TAILLE_MAX + 1))).toBe(MESSAGE_TAILLE);
  });

  it('US-15 CA1, CA2, CA3 : messages du dépôt', () => {
    const xlsx = { nom: 'releve.xlsx', motif: MESSAGE_FORMAT };
    expect(resumeDepot(1, [])).toBe('1 facture déposée');
    expect(resumeDepot(3, [])).toBe('3 factures déposées');
    expect(resumeDepot(0, [xlsx])).toBe('Format non accepté : PDF, JPG ou PNG uniquement');
    expect(resumeDepot(2, [xlsx])).toBe('2 factures déposées, 1 refusée');
    expect(resumeDepot(1, [xlsx, xlsx])).toBe('1 facture déposée, 2 refusées');
    expect(resumeDepot(0, [xlsx, xlsx])).toBe('Aucune facture déposée, 2 refusées');
  });
});

describe('Espace client du dirigeant (US-15, US-38, US-16)', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [EspaceClient],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).ouvrir({ jeton: 'jeton', role: 'DIRIGEANT', nomAffiche: 'Sophie Martin' });
  });

  afterEach(() => http.verify());

  async function ouvrir(factures: FactureDeposee[] = [TICKET, LOYER]) {
    const fixture: ComponentFixture<EspaceClient> = TestBed.createComponent(EspaceClient);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Chargement…');
    http.expectOne(`${URL}?sens=ACHAT`).flush(factures);
    await fixture.whenStable();
    return { fixture, page };
  }

  function lignes(page: HTMLElement) {
    return Array.from(page.querySelectorAll('tbody tr')).map((tr) =>
      Array.from(tr.querySelectorAll('td')).map((td) => td.textContent?.replace(/\s+/g, ' ').trim()),
    );
  }

  function onglets(page: HTMLElement) {
    return Array.from(page.querySelectorAll<HTMLButtonElement>('[role=tab]'));
  }

  function bouton(page: HTMLElement, texte: string): HTMLButtonElement {
    const trouve = Array.from(page.querySelectorAll<HTMLButtonElement>('button')).find(
      (b) => b.textContent?.trim() === texte,
    );
    expect(trouve).toBeDefined();
    return trouve as HTMLButtonElement;
  }

  it('US-16 RG1 : mes dépôts avec la date ; CA2 : pas de « Supprimer » si le cabinet l’a ouverte', async () => {
    const { page } = await ouvrir();
    expect(page.querySelector('h1')?.textContent).toBe('Bonjour Sophie Martin');
    expect(lignes(page)).toEqual([
      ['ticket-carburant.jpg', '08/11/2026 à 10:05', 'Supprimer'],
      ['loyer.pdf', '08/11/2026 à 10:05', 'Ouverte par le cabinet'],
    ]);
    expect(page.querySelectorAll('.supprimer')).toHaveLength(1);
  });

  it('US-38 RG1 : choix de l’espace, Factures fournisseurs par défaut', async () => {
    const { fixture, page } = await ouvrir([]);
    expect(onglets(page).map((o) => o.textContent?.trim())).toEqual([
      'Factures fournisseurs (achats)',
      'Factures clients (ventes)',
    ]);
    expect(page.querySelector('.vide')?.textContent).toBe('Aucune facture fournisseur déposée pour le moment');

    onglets(page)[1].click();
    await fixture.whenStable();
    http.expectOne(`${URL}?sens=VENTE`).flush([]);
    await fixture.whenStable();
    expect(onglets(page)[1].getAttribute('aria-selected')).toBe('true');
    expect(page.querySelector('.vide')?.textContent).toBe('Aucune facture client déposée pour le moment');
  });

  it('US-38 CA1 : une facture déposée dans Factures clients part vers les ventes', async () => {
    const { fixture, page } = await ouvrir([]);
    onglets(page)[1].click();
    await fixture.whenStable();
    http.expectOne(`${URL}?sens=VENTE`).flush([]);

    fixture.componentInstance.deposer([fichier('facture-client-FC-2026-031.pdf', 'application/pdf')]);
    const envoi = http.expectOne(`${URL}?sens=VENTE`);
    expect(envoi.request.method).toBe('POST');
    expect((envoi.request.body as FormData).get('fichier')).toBeInstanceOf(File);
    envoi.flush({ ...TICKET, nomFichier: 'facture-client-FC-2026-031.pdf', sens: 'VENTE' });
    await fixture.whenStable();
    http.expectOne(`${URL}?sens=VENTE`).flush([{ ...TICKET, nomFichier: 'facture-client-FC-2026-031.pdf' }]);
    await fixture.whenStable();
    expect(page.querySelector('.resultat__message')?.textContent).toBe('1 facture déposée');
    expect(lignes(page)[0][0]).toBe('facture-client-FC-2026-031.pdf');
  });

  it('US-15 CA2 : mauvais format refusé sans rien envoyer', async () => {
    const { fixture, page } = await ouvrir([]);
    fixture.componentInstance.deposer([fichier('releve.xlsx', 'application/vnd.ms-excel')]);
    await fixture.whenStable();
    expect(page.querySelector('.resultat__message')?.textContent).toBe(MESSAGE_FORMAT);
    expect(page.querySelector('.resultat__fichier')?.textContent).toBe('releve.xlsx');
    expect(page.querySelector('.resultat--refus')).not.toBeNull();
  });

  it('US-15 CA3 / RG2 : lot mixte, chaque fichier part seul, un refus n’arrête pas les autres', async () => {
    const { fixture, page } = await ouvrir([]);
    fixture.componentInstance.deposer([
      fichier('a.pdf', 'application/pdf'),
      fichier('releve.xlsx', ''),
      fichier('b.png', 'image/png'),
      fichier('faux.pdf', 'application/pdf'),
    ]);
    http.expectOne(`${URL}?sens=ACHAT`).flush(TICKET);
    // Refus du serveur (vrai type vérifié) : son message.
    const b = http.expectOne(`${URL}?sens=ACHAT`);
    b.flush(TICKET);
    http
      .expectOne(`${URL}?sens=ACHAT`)
      .flush({ message: MESSAGE_FORMAT }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    http.expectOne(`${URL}?sens=ACHAT`).flush([TICKET, TICKET]);
    await fixture.whenStable();
    expect(page.querySelector('.resultat__message')?.textContent).toBe('2 factures déposées, 2 refusées');
    expect(Array.from(page.querySelectorAll('.resultat li')).map((li) => li.textContent?.trim())).toEqual([
      `releve.xlsx : ${MESSAGE_FORMAT}`,
      `faux.pdf : ${MESSAGE_FORMAT}`,
    ]);
  });

  it('panne pendant un envoi : fichier refusé « indisponible »', async () => {
    const { fixture, page } = await ouvrir([]);
    fixture.componentInstance.deposer([fichier('a.pdf', 'application/pdf')]);
    http.expectOne(`${URL}?sens=ACHAT`).flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(page.querySelector('.resultat__message')?.textContent).toBe(INDISPONIBLE);
  });

  it('US-15 RG1 : 20 fichiers maximum par dépôt', async () => {
    const { fixture, page } = await ouvrir([]);
    fixture.componentInstance.deposer(Array.from({ length: 21 }, (_, i) => fichier(`f${i}.pdf`, 'application/pdf')));
    fixture.componentInstance.deposer([]);
    await fixture.whenStable();
    expect(page.querySelector('.resultat__message')?.textContent).toBe(MESSAGE_TROP_DE_FICHIERS);
  });

  it('RG1 : glisser-déposer et « Choisir des fichiers »', async () => {
    const { fixture, page } = await ouvrir([]);
    const zone = page.querySelector('.zone') as HTMLElement;
    const survol = new Event('dragover', { cancelable: true });
    zone.dispatchEvent(survol);
    await fixture.whenStable();
    expect(survol.defaultPrevented).toBe(true);
    expect(zone.classList).toContain('zone--survol');
    zone.dispatchEvent(new Event('dragleave'));
    await fixture.whenStable();
    expect(zone.classList).not.toContain('zone--survol');

    const lacher = Object.assign(new Event('drop', { cancelable: true }), {
      dataTransfer: { files: [fichier('a.pdf', 'application/pdf')] },
    });
    zone.dispatchEvent(lacher);
    http.expectOne(`${URL}?sens=ACHAT`).flush(TICKET);
    await fixture.whenStable();
    http.expectOne(`${URL}?sens=ACHAT`).flush([TICKET]);

    const champ = page.querySelector('input[type=file]') as HTMLInputElement;
    Object.defineProperty(champ, 'files', { value: [fichier('b.png', 'image/png')], configurable: true });
    champ.dispatchEvent(new Event('change'));
    http.expectOne(`${URL}?sens=ACHAT`).flush(TICKET);
    await fixture.whenStable();
    http.expectOne(`${URL}?sens=ACHAT`).flush([TICKET]);
    await fixture.whenStable();
    expect(page.querySelector('.resultat__message')?.textContent).toBe('1 facture déposée');
  });

  it('US-16 CA1 : « Supprimer » puis confirmer : la facture disparaît', async () => {
    const { fixture, page } = await ouvrir();
    (page.querySelector('.supprimer') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(page.textContent).toContain('Supprimer cette facture ?');

    bouton(page, 'Annuler').click();
    await fixture.whenStable();
    expect(page.textContent).not.toContain('Supprimer cette facture ?');

    (page.querySelector('.supprimer') as HTMLButtonElement).click();
    await fixture.whenStable();
    bouton(page, 'Oui, supprimer').click();
    const suppression = http.expectOne(`${URL}/7`);
    expect(suppression.request.method).toBe('DELETE');
    suppression.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();
    expect(lignes(page).map((l) => l[0])).toEqual(['loyer.pdf']);
    expect(page.querySelector('.info')?.textContent).toBe(MESSAGE_SUPPRIMEE);
  });

  it('US-16 RG3 : ouverte par le cabinet entre-temps : message et liste rafraîchie', async () => {
    const { fixture, page } = await ouvrir();
    (page.querySelector('.supprimer') as HTMLButtonElement).click();
    await fixture.whenStable();
    bouton(page, 'Oui, supprimer').click();
    const message = 'Votre cabinet a déjà ouvert cette facture : elle ne peut plus être supprimée';
    http.expectOne(`${URL}/7`).flush({ message }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();
    http.expectOne(`${URL}?sens=ACHAT`).flush([{ ...TICKET, supprimable: false }]);
    await fixture.whenStable();
    expect(page.querySelector('.info')?.textContent).toBe(message);
    expect(page.querySelector('.supprimer')).toBeNull();
  });

  it('suppression impossible : service indisponible', async () => {
    const { fixture, page } = await ouvrir();
    fixture.componentInstance.confirmerSuppression(TICKET);
    http.expectOne(`${URL}/7`).flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(page.querySelector('.info')?.textContent).toBe(INDISPONIBLE);
  });

  it('liste indisponible', async () => {
    const fixture = TestBed.createComponent(EspaceClient);
    await fixture.whenStable();
    http.expectOne(`${URL}?sens=ACHAT`).flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent).toBe(INDISPONIBLE);
  });
});
