import { Injectable, signal } from '@angular/core';
import { Role, SessionActive, SessionOuverte } from '../connexion/auth.service';

const CLE = 'pointeo.session';

interface SessionStockee {
  jeton: string;
  utilisateur: SessionActive;
}

/** Écran d'arrivée après la connexion (US-03 RG3). */
export function accueil(role: Role): string {
  return role === 'COLLABORATEUR' ? '/dossiers' : '/espace-client';
}

/**
 * Session de l'onglet (US-07) : jeton reçu après le code SMS, gardé le temps de
 * l'onglet (sessionStorage) et effacé à la déconnexion ou à l'expiration.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  readonly utilisateur = signal<SessionActive | null>(this.lire()?.utilisateur ?? null);

  jeton(): string | null {
    return this.lire()?.jeton ?? null;
  }

  ouvrir(session: SessionOuverte): void {
    const utilisateur = { nomAffiche: session.nomAffiche, role: session.role, administrateur: session.administrateur };
    this.ecrire({ jeton: session.jeton, utilisateur });
  }

  mettreAJour(utilisateur: SessionActive): void {
    const jeton = this.jeton();
    if (jeton) {
      this.ecrire({ jeton, utilisateur });
    }
  }

  fermer(): void {
    sessionStorage.removeItem(CLE);
    this.utilisateur.set(null);
  }

  private ecrire(session: SessionStockee): void {
    sessionStorage.setItem(CLE, JSON.stringify(session));
    this.utilisateur.set(session.utilisateur);
  }

  private lire(): SessionStockee | null {
    try {
      return JSON.parse(sessionStorage.getItem(CLE) ?? 'null') as SessionStockee | null;
    } catch {
      return null;
    }
  }
}
