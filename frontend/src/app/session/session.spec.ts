import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom, isObservable, of } from 'rxjs';
import { AuthService } from '../connexion/auth.service';
import { authInterceptor } from './auth.interceptor';
import {
  AVERTISSEMENT_MS,
  DUREE_INACTIVITE_MS,
  InactiviteService,
  SIGNAL_ACTIVITE_MS,
} from './inactivite.service';
import { sessionGuard } from './session.guard';
import { SessionService, accueil } from './session.service';

const JEAN = { jeton: 'jeton-jean', role: 'COLLABORATEUR' as const, nomAffiche: 'Jean Dupont' };

describe('Session (US-07)', () => {
  let http: HttpTestingController;
  let session: SessionService;
  let router: Router;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionService);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  describe('SessionService', () => {
    it('ouvre, met à jour et ferme la session de l’onglet', () => {
      expect(session.jeton()).toBeNull();
      session.ouvrir(JEAN);
      expect(session.jeton()).toBe('jeton-jean');
      expect(session.utilisateur()).toEqual({ nomAffiche: 'Jean Dupont', role: 'COLLABORATEUR' });
      session.mettreAJour({ nomAffiche: 'Jean D.', role: 'COLLABORATEUR' });
      expect(session.utilisateur()?.nomAffiche).toBe('Jean D.');
      session.fermer();
      expect(session.jeton()).toBeNull();
      expect(session.utilisateur()).toBeNull();
      session.mettreAJour({ nomAffiche: 'X', role: 'DIRIGEANT' });
      expect(session.utilisateur()).toBeNull();
    });

    it('ignore une session stockée illisible', () => {
      sessionStorage.setItem('pointeo.session', '{abîmé');
      expect(session.jeton()).toBeNull();
    });

    it('US-03 RG3 : écran d’arrivée selon le rôle', () => {
      expect(accueil('COLLABORATEUR')).toBe('/dossiers');
      expect(accueil('DIRIGEANT')).toBe('/espace-client');
    });
  });

  describe('authInterceptor', () => {
    it('ajoute le jeton aux appels du backend seulement', () => {
      const client = TestBed.inject(HttpClient);
      client.get('/api/clients').subscribe();
      expect(http.expectOne('/api/clients').request.headers.has('Authorization')).toBe(false);

      session.ouvrir(JEAN);
      client.get('/api/clients').subscribe();
      client.get('https://autre-site.fr/x').subscribe();
      expect(http.expectOne('/api/clients').request.headers.get('Authorization')).toBe('Bearer jeton-jean');
      expect(http.expectOne('https://autre-site.fr/x').request.headers.has('Authorization')).toBe(false);
    });
  });

  describe('AuthService', () => {
    it('déconnexion avec ou sans jeton', () => {
      const auth = TestBed.inject(AuthService);
      auth.deconnecter('j').subscribe();
      expect(http.expectOne('/api/auth/deconnexion').request.headers.get('Authorization')).toBe('Bearer j');
      auth.deconnecter(null).subscribe();
      expect(http.expectOne('/api/auth/deconnexion').request.headers.has('Authorization')).toBe(false);
    });
  });

  describe('sessionGuard', () => {
    const lancer = (role?: string, administrateur?: boolean) =>
      TestBed.runInInjectionContext(() =>
        sessionGuard(
          { data: { ...(role ? { role } : {}), ...(administrateur ? { administrateur } : {}) } } as unknown as ActivatedRouteSnapshot,
          {} as RouterStateSnapshot,
        ),
      );
    const resultat = async (sortie: ReturnType<typeof lancer>) =>
      isObservable(sortie) ? firstValueFrom(sortie as Observable<boolean | UrlTree>) : sortie;
    const url = (valeur: unknown) => router.serializeUrl(valeur as UrlTree);

    it('sans session -> écran de connexion, sans appel au backend', async () => {
      expect(url(await resultat(lancer()))).toBe('/connexion');
    });

    it('session active -> page ouverte', async () => {
      session.ouvrir(JEAN);
      const sortie = resultat(lancer('COLLABORATEUR'));
      http.expectOne('/api/auth/session').flush({ nomAffiche: 'Jean Dupont', role: 'COLLABORATEUR' });
      expect(await sortie).toBe(true);
    });

    it('mauvais rôle -> renvoyé vers son propre écran', async () => {
      session.ouvrir(JEAN);
      const sortie = resultat(lancer('DIRIGEANT'));
      http.expectOne('/api/auth/session').flush({ nomAffiche: 'Jean Dupont', role: 'COLLABORATEUR' });
      expect(url(await sortie)).toBe('/dossiers');
    });

    it('US-19 RG1 : page « Équipe » réservée aux administrateurs du cabinet', async () => {
      session.ouvrir(JEAN);
      const refusee = resultat(lancer('COLLABORATEUR', true));
      http
        .expectOne('/api/auth/session')
        .flush({ nomAffiche: 'Jean Dupont', role: 'COLLABORATEUR', administrateur: false });
      expect(url(await refusee)).toBe('/dossiers');

      const ouverte = resultat(lancer('COLLABORATEUR', true));
      http
        .expectOne('/api/auth/session')
        .flush({ nomAffiche: 'Claire Martin', role: 'COLLABORATEUR', administrateur: true });
      expect(await ouverte).toBe(true);
    });

    it('CA2 : session expirée -> connexion avec « Votre session a expiré »', async () => {
      session.ouvrir(JEAN);
      const sortie = resultat(lancer());
      http
        .expectOne('/api/auth/session')
        .flush({ message: 'Votre session a expiré', code: 'SESSION_EXPIREE' }, { status: 401, statusText: 'X' });
      expect(url(await sortie)).toBe('/connexion?raison=session-expiree');
      expect(session.jeton()).toBeNull();
    });

    it('session fermée -> connexion sans message', async () => {
      session.ouvrir(JEAN);
      const sortie = resultat(lancer());
      http.expectOne('/api/auth/session').flush({ code: 'NON_CONNECTE' }, { status: 401, statusText: 'X' });
      expect(url(await sortie)).toBe('/connexion');
    });
  });

  describe('InactiviteService', () => {
    let inactivite: InactiviteService;
    let navigate: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
      vi.useFakeTimers();
      session.ouvrir(JEAN);
      inactivite = TestBed.inject(InactiviteService);
      navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
      inactivite.demarrer();
    });

    afterEach(() => inactivite.arreter());

    it('RG2 : avertissement 2 minutes avant, puis RG3 : expiration à 30 minutes', () => {
      vi.advanceTimersByTime(DUREE_INACTIVITE_MS - AVERTISSEMENT_MS - 1000);
      expect(inactivite.secondesRestantes()).toBeNull();
      vi.advanceTimersByTime(1000);
      expect(inactivite.secondesRestantes()).toBe(120);

      document.dispatchEvent(new Event('mousemove'));
      vi.advanceTimersByTime(AVERTISSEMENT_MS);
      const deconnexion = http.expectOne('/api/auth/deconnexion');
      expect(deconnexion.request.headers.get('Authorization')).toBe('Bearer jeton-jean');
      deconnexion.flush(null);
      expect(session.jeton()).toBeNull();
      expect(navigate).toHaveBeenCalledWith(['/connexion'], { queryParams: { raison: 'session-expiree' } });
    });

    it('l’activité repousse l’expiration et prévient le backend toutes les 5 minutes', () => {
      vi.advanceTimersByTime(SIGNAL_ACTIVITE_MS);
      document.dispatchEvent(new Event('click'));
      http.expectOne('/api/auth/session').flush({ nomAffiche: 'Jean Dupont', role: 'COLLABORATEUR' });
      vi.advanceTimersByTime(DUREE_INACTIVITE_MS - AVERTISSEMENT_MS - 1000);
      expect(inactivite.secondesRestantes()).toBeNull();
      expect(navigate).not.toHaveBeenCalled();
    });

    it('« Rester connecté » ferme l’avertissement et prolonge la session', () => {
      vi.advanceTimersByTime(DUREE_INACTIVITE_MS - AVERTISSEMENT_MS);
      expect(inactivite.secondesRestantes()).not.toBeNull();
      inactivite.resterConnecte();
      http.expectOne('/api/auth/session').flush({ nomAffiche: 'Jean Dupont', role: 'COLLABORATEUR' });
      expect(inactivite.secondesRestantes()).toBeNull();
      vi.advanceTimersByTime(AVERTISSEMENT_MS + 1000);
      expect(navigate).not.toHaveBeenCalled();
    });

    it('session déjà expirée côté serveur -> expiration immédiate', () => {
      inactivite.resterConnecte();
      http.expectOne('/api/auth/session').flush({ code: 'SESSION_EXPIREE' }, { status: 401, statusText: 'X' });
      http.expectOne('/api/auth/deconnexion').flush(null);
      expect(navigate).toHaveBeenCalledWith(['/connexion'], { queryParams: { raison: 'session-expiree' } });
    });

    it('erreur réseau pendant le signal -> rien ne change', () => {
      inactivite.resterConnecte();
      http.expectOne('/api/auth/session').flush(null, { status: 0, statusText: 'Réseau' });
      expect(navigate).not.toHaveBeenCalled();
    });
  });
});

// Utilisé par isObservable : évite l’import inutilisé si la garde renvoie une valeur directe.
void of;
