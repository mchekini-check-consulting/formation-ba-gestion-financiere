import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

/** US-41 Compte du plan comptable d'un dossier (RG1 : plan général ou sous-compte ajouté). */
export interface CompteComptable {
  numero: string;
  libelle: string;
  sousCompte: boolean;
  /** RG3 : un compte désactivé ne se choisit plus dans une écriture. */
  actif: boolean;
}

/** US-41 RG2 : compte par défaut d'une catégorie (Achat → 607…) ou de la banque (512). */
export interface CompteParDefaut {
  code: string;
  libelle: string;
  numero: string;
}

export interface PlanComptable {
  comptes: CompteComptable[];
  parDefaut: CompteParDefaut[];
}

/** Formulaire « Nouveau compte ». */
export interface NouveauCompte {
  numero: string;
  libelle: string;
}

/** Réponse 400 / 409 du backend : un message par champ à corriger. */
export interface ErreursFormulaire {
  message: string;
  erreurs: Partial<Record<keyof NouveauCompte, string>>;
}

/** US-41 Plan comptable d'un dossier : appels au backend (via le gateway). */
@Injectable({ providedIn: 'root' })
export class PlanComptableService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase()}/dossiers`;

  consulter(dossierId: number): Observable<PlanComptable> {
    return this.http.get<PlanComptable>(this.plan(dossierId));
  }

  creer(dossierId: number, compte: NouveauCompte): Observable<CompteComptable> {
    return this.http.post<CompteComptable>(this.plan(dossierId), compte);
  }

  changerActivation(dossierId: number, numero: string, actif: boolean): Observable<CompteComptable> {
    return this.http.patch<CompteComptable>(`${this.plan(dossierId)}/${encodeURIComponent(numero)}`, { actif });
  }

  supprimer(dossierId: number, numero: string): Observable<void> {
    return this.http.delete<void>(`${this.plan(dossierId)}/${encodeURIComponent(numero)}`);
  }

  private plan(dossierId: number): string {
    return `${this.url}/${dossierId}/plan-comptable`;
  }
}

/** Recherche dans le numéro ou le libellé (sans tenir compte des majuscules ni des accents). */
export function filtrerComptes(comptes: CompteComptable[], recherche: string): CompteComptable[] {
  const cherche = normaliser(recherche.trim());
  return cherche === ''
    ? comptes
    : comptes.filter((c) => normaliser(c.numero).includes(cherche) || normaliser(c.libelle).includes(cherche));
}

/** Plan trié par numéro, comme le backend (401, 401MOULINS, 404). */
export function trierComptes(comptes: CompteComptable[]): CompteComptable[] {
  return [...comptes].sort((a, b) => (a.numero < b.numero ? -1 : a.numero > b.numero ? 1 : 0));
}

function normaliser(texte: string): string {
  return texte.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}
