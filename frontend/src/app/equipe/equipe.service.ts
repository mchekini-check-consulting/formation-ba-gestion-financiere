import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

export type RoleCabinet = 'ADMINISTRATEUR' | 'ASSOCIE' | 'COMPTABLE';

export const ROLES: { valeur: RoleCabinet; libelle: string }[] = [
  { valeur: 'ADMINISTRATEUR', libelle: 'Administrateur' },
  { valeur: 'ASSOCIE', libelle: 'Associé' },
  { valeur: 'COMPTABLE', libelle: 'Comptable' },
];

/** Collaborateur du cabinet : ACTIF (compte activé) ou INVITE (invitation envoyée). */
export interface Membre {
  id: number;
  nomAffiche: string;
  email: string;
  role: RoleCabinet;
  statut: 'ACTIF' | 'INVITE';
}

export interface DemandeInvitation {
  prenom: string;
  nom: string;
  email: string;
  role: RoleCabinet | '';
}

export interface InvitationEnvoyee {
  membre: Membre;
}

/** US-19 Inviter un collaborateur avec un rôle : appels au backend (via le gateway). */
@Injectable({ providedIn: 'root' })
export class EquipeService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase()}/equipe`;

  membres(): Observable<Membre[]> {
    return this.http.get<Membre[]>(this.url);
  }

  inviter(demande: DemandeInvitation): Observable<InvitationEnvoyee> {
    return this.http.post<InvitationEnvoyee>(`${this.url}/invitations`, demande);
  }
}
