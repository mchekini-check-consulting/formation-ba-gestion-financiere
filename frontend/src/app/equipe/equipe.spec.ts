import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Equipe, erreursInvitation } from './equipe';
import { Membre } from './equipe.service';

const CLAIRE: Membre = {
  id: 1,
  nomAffiche: 'Claire Martin',
  email: 'claire.martin@cabinet-test.fr',
  role: 'ADMINISTRATEUR',
  statut: 'ACTIF',
};
const LUCIE: Membre = {
  id: 2,
  nomAffiche: 'Lucie Moreau',
  email: 'lucie.moreau@cabinet-test.fr',
  role: 'COMPTABLE',
  statut: 'INVITE',
};
const DEJA_UTILISEE = 'Cette adresse est déjà utilisée';
const OBLIGATOIRE = 'Ce champ est obligatoire';

describe('Équipe du cabinet (US-19)', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Equipe],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function ouvrir(membres: Membre[] = [CLAIRE]) {
    const fixture = TestBed.createComponent(Equipe);
    await fixture.whenStable();
    http.expectOne('/api/equipe').flush(membres);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const stable = () => fixture.whenStable();
    const texte = (selecteur: string) => page.querySelector(selecteur)?.textContent?.trim();
    const lignes = () =>
      [...page.querySelectorAll('tbody tr')].map((tr) =>
        [...tr.querySelectorAll('td')].map((td) => td.textContent?.trim()),
      );
    const saisir = async (selecteur: string, valeur: string) => {
      const champ = page.querySelector<HTMLInputElement | HTMLSelectElement>(selecteur)!;
      champ.value = valeur;
      champ.dispatchEvent(new Event(champ instanceof HTMLSelectElement ? 'change' : 'input'));
      await stable();
    };
    const remplir = async (email = 'lucie.moreau@cabinet-test.fr', role = 'COMPTABLE') => {
      await saisir('#prenom', 'Lucie');
      await saisir('#nom', 'Moreau');
      await saisir('#email', email);
      await saisir('#role', role);
    };
    const envoyer = async () => {
      page.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
      await stable();
    };
    return { page, stable, texte, lignes, remplir, envoyer };
  }

  it('affiche les collaborateurs du cabinet avec rôle et statut', async () => {
    const { lignes } = await ouvrir([CLAIRE, LUCIE]);
    expect(lignes()).toEqual([
      ['Claire Martin', 'claire.martin@cabinet-test.fr', 'Administrateur', 'Actif'],
      ['Lucie Moreau', 'lucie.moreau@cabinet-test.fr', 'Comptable', 'Invitée'],
    ]);
  });

  it('CA1 : Claire invite Lucie comme Comptable, Lucie apparaît « Invitée »', async () => {
    const { page, stable, texte, lignes, remplir, envoyer } = await ouvrir();
    await remplir();
    await envoyer();
    const requete = http.expectOne('/api/equipe/invitations');
    expect(requete.request.method).toBe('POST');
    expect(requete.request.body).toEqual({
      prenom: 'Lucie',
      nom: 'Moreau',
      email: 'lucie.moreau@cabinet-test.fr',
      role: 'COMPTABLE',
    });
    requete.flush({ membre: LUCIE, jetonDeTest: 'jeton-lien' }, { status: 201, statusText: 'Created' });
    await stable();

    expect(texte('.succes')).toContain('Invitation envoyée à lucie.moreau@cabinet-test.fr');
    // Le lien arrive par email (Mailpit en dev/qualif) : jamais affiché, même si le backend le renvoie.
    expect(page.querySelector('a[href*="activer-compte"]')).toBeNull();
    expect(lignes()[1]).toEqual(['Lucie Moreau', 'lucie.moreau@cabinet-test.fr', 'Comptable', 'Invitée']);
    expect(page.querySelector<HTMLInputElement>('#prenom')!.value).toBe('');
  });

  it('CA2 : adresse déjà utilisée -> message, sous l’email aussi', async () => {
    const { page, stable, texte, lignes, remplir, envoyer } = await ouvrir();
    await remplir('sophie.leroy@boulangerie-leroy.fr', 'ASSOCIE');
    await envoyer();
    http
      .expectOne('/api/equipe/invitations')
      .flush({ message: DEJA_UTILISEE, erreurs: { email: DEJA_UTILISEE } }, { status: 409, statusText: 'Conflict' });
    await stable();
    expect(texte('.alerte')).toBe(DEJA_UTILISEE);
    expect(texte('#email + .erreur')).toBe(DEJA_UTILISEE);
    expect(page.querySelector('#email')?.getAttribute('aria-invalid')).toBe('true');
    expect(lignes().length).toBe(1);
  });

  it('formulaire incomplet -> erreurs par champ, rien n’est envoyé', async () => {
    const { page, texte, envoyer } = await ouvrir();
    await envoyer();
    expect(texte('.alerte')).toBe('Certains champs sont à corriger');
    expect([...page.querySelectorAll('.erreur')].map((e) => e.textContent?.trim())).toEqual([
      OBLIGATOIRE,
      OBLIGATOIRE,
      OBLIGATOIRE,
      'Choisissez un rôle',
    ]);
    http.expectNone('/api/equipe/invitations');
  });

  it('service indisponible -> message générique', async () => {
    const { stable, texte, remplir, envoyer } = await ouvrir();
    await remplir();
    await envoyer();
    http.expectOne('/api/equipe/invitations').flush(null, { status: 503, statusText: 'Unavailable' });
    await stable();
    expect(texte('.alerte')).toBe('Le service est momentanément indisponible. Réessayez.');
  });

  it('liste indisponible -> message générique', async () => {
    const fixture = TestBed.createComponent(Equipe);
    await fixture.whenStable();
    http.expectOne('/api/equipe').flush(null, { status: 500, statusText: 'Error' });
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('.alerte')?.textContent).toContain('momentanément indisponible');
    expect(page.querySelector('table')).not.toBeNull();
  });

  it('règles du formulaire', () => {
    expect(erreursInvitation({ prenom: 'Lucie', nom: 'Moreau', email: 'l@c.fr', role: 'ASSOCIE' })).toEqual({});
    expect(erreursInvitation({ prenom: ' ', nom: 'Moreau', email: 'lucie', role: 'ASSOCIE' })).toEqual({
      prenom: OBLIGATOIRE,
      email: 'Adresse email invalide',
    });
  });
});
