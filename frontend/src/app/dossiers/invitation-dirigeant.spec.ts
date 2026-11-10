import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionService } from '../session/session.service';
import { EtatEspaceClient } from './dossier.service';
import { InvitationDirigeant, MESSAGE_RENVOI, erreursInvitationDirigeant } from './invitation-dirigeant';

describe('US-14 Inviter le dirigeant', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [InvitationDirigeant],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).ouvrir({ jeton: 'jeton', role: 'COLLABORATEUR', nomAffiche: 'Jean Dupont' });
  });

  afterEach(() => http.verify());

  async function afficher(etat: EtatEspaceClient) {
    const fixture: ComponentFixture<InvitationDirigeant> = TestBed.createComponent(InvitationDirigeant);
    fixture.componentRef.setInput('dossierId', 42);
    fixture.componentRef.setInput('etat', etat);
    const etats: EtatEspaceClient[] = [];
    fixture.componentInstance.invite.subscribe((e) => etats.push(e));
    await fixture.whenStable();
    return { fixture, page: fixture.nativeElement as HTMLElement, etats };
  }

  function bouton(page: HTMLElement, texte: string) {
    return Array.from(page.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.trim() === texte);
  }

  function saisir(page: HTMLElement, id: string, valeur: string) {
    const champ = page.querySelector<HTMLInputElement>(`#${id}`)!;
    champ.value = valeur;
    champ.dispatchEvent(new Event('input'));
  }

  async function remplir(fixture: ComponentFixture<InvitationDirigeant>, page: HTMLElement, email: string) {
    bouton(page, 'Inviter le dirigeant')?.click();
    await fixture.whenStable();
    saisir(page, 'prenom-dirigeant', 'Sophie');
    saisir(page, 'nom-dirigeant', 'Martin');
    saisir(page, 'email-dirigeant', email);
    bouton(page, "Envoyer l'invitation")?.click();
    await fixture.whenStable();
  }

  it('RG1 et RG3 : prénom, nom, email ; l’espace passe à « Invité »', async () => {
    const { fixture, page, etats } = await afficher('NON_INVITE');
    expect(page.querySelector('form')).toBeNull();
    await remplir(fixture, page, ' sophie.martin@boulangerie-test.fr ');

    const envoi = http.expectOne('/api/dossiers/42/invitation');
    expect(envoi.request.method).toBe('POST');
    // Le champ email du navigateur retire lui-même les espaces autour de l'adresse.
    expect(envoi.request.body).toEqual({
      prenom: 'Sophie',
      nom: 'Martin',
      email: 'sophie.martin@boulangerie-test.fr',
    });
    envoi.flush({ etatEspaceClient: 'INVITE', jetonDeTest: 'abc' }, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(etats).toEqual(['INVITE']);
    expect(page.querySelector('form')).toBeNull();
    expect(page.querySelector('[role=status]')?.textContent).toContain(
      'Invitation envoyée à sophie.martin@boulangerie-test.fr',
    );
    // Le lien arrive par email (Mailpit en dev/qualif) : jamais affiché, même si le backend le renvoie.
    expect(page.querySelector('a[href*="activer-compte"]')).toBeNull();
  });

  it('CA erreur : « Saisissez une adresse email valide », rien n’est envoyé', async () => {
    const { fixture, page } = await afficher('NON_INVITE');
    await remplir(fixture, page, 'sophie@');
    expect(page.querySelector('.erreur')?.textContent).toBe('Saisissez une adresse email valide');
    http.expectNone('/api/dossiers/42/invitation');

    expect(erreursInvitationDirigeant({ prenom: ' ', nom: '', email: '' })).toEqual({
      prenom: 'Ce champ est obligatoire',
      nom: 'Ce champ est obligatoire',
      email: 'Saisissez une adresse email valide',
    });
  });

  it('refus du serveur affiché sur le champ ; « Annuler » ferme le formulaire', async () => {
    const { fixture, page, etats } = await afficher('NON_INVITE');
    await remplir(fixture, page, 'sophie@test.fr');
    http.expectOne('/api/dossiers/42/invitation').flush(
      { message: 'Cette adresse est déjà utilisée', erreurs: { email: 'Cette adresse est déjà utilisée' } },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();
    expect(page.querySelector('.erreur')?.textContent).toBe('Cette adresse est déjà utilisée');
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Cette adresse est déjà utilisée');
    expect(etats).toEqual([]);

    bouton(page, 'Annuler')?.click();
    await fixture.whenStable();
    expect(page.querySelector('form')).toBeNull();
    expect(bouton(page, 'Inviter le dirigeant')).toBeDefined();
  });

  it('RG2 : « Renvoyer l’invitation » quand le dirigeant est invité', async () => {
    const { fixture, page, etats } = await afficher('INVITE');
    expect(bouton(page, 'Inviter le dirigeant')).toBeUndefined();
    bouton(page, "Renvoyer l'invitation")?.click();
    await fixture.whenStable();
    expect(bouton(page, 'Envoi…')?.disabled).toBe(true);
    // Un second clic pendant l'envoi ne renvoie rien.
    fixture.componentInstance.renvoyer();

    http.expectOne('/api/dossiers/42/invitation/renvoi').flush({ etatEspaceClient: 'INVITE', jetonDeTest: null });
    await fixture.whenStable();
    expect(page.querySelector('[role=status]')?.textContent?.trim()).toBe(MESSAGE_RENVOI);
    expect(page.querySelector('a[href*="activer-compte"]')).toBeNull();
    expect(etats).toEqual(['INVITE']);
  });

  it('renvoi impossible ou service indisponible', async () => {
    const { fixture, page } = await afficher('INVITE');
    bouton(page, "Renvoyer l'invitation")?.click();
    await fixture.whenStable();
    http.expectOne('/api/dossiers/42/invitation/renvoi').flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('momentanément indisponible');
  });

  it('espace actif : ni invitation ni renvoi', async () => {
    const { page } = await afficher('ACTIF');
    expect(page.querySelectorAll('button')).toHaveLength(0);
  });

  it('un second envoi pendant le premier est ignoré', async () => {
    const { fixture, page } = await afficher('NON_INVITE');
    await remplir(fixture, page, 'sophie@test.fr');
    fixture.componentInstance.inviter();
    http.expectOne('/api/dossiers/42/invitation').flush({ etatEspaceClient: 'INVITE', jetonDeTest: null });
  });
});
