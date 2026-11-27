import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';
import { LigneEcriture } from './facture.service';

/** US-47 RG1 : période du journal (« 2026-06 » : un mois ; « 2026 » : l'exercice). */
export interface Periode {
  code: string;
  libelle: string;
}

/** US-47 Écriture du journal ; exportee : lecture seule (RG2, CA3) ; modifiee* : dernière correction (RG4). */
export interface EcritureJournal {
  id: number;
  journal: 'AC' | 'VT';
  date: string;
  piece: string | null;
  libelle: string;
  exportee: boolean;
  modifieeLe: string | null;
  modifieePar: string | null;
  lignes: LigneEcriture[];
  totalDebit: number;
  totalCredit: number;
}

/** US-47 RG1 : journal d'une période ; periode null : aucune écriture dans le dossier. */
export interface JournalEcritures {
  periode: string | null;
  periodes: Periode[];
  ecritures: EcritureJournal[];
  totalDebit: number;
  totalCredit: number;
}

/** US-47 RG2 : correction envoyée (libellé, et compte, débit, crédit de chaque ligne dans l'ordre). */
export interface CorrectionEcriture {
  libelle: string;
  lignes: { numeroCompte: string; debit: number; credit: number }[];
}

/** Journaux connus, pour le filtre « Journal ». */
export const LIBELLES_JOURNAUX: Record<string, string> = {
  AC: 'AC – Achats',
  VT: 'VT – Ventes',
};

/** US-47 Journal d'un dossier : appels au backend (via le gateway). */
@Injectable({ providedIn: 'root' })
export class EcritureService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase()}/dossiers`;

  /** RG1 : sans période, le mois le plus récent qui a des écritures. */
  consulter(dossierId: number, periode: string | null): Observable<JournalEcritures> {
    const params = periode ? new HttpParams().set('periode', periode) : undefined;
    return this.http.get<JournalEcritures>(`${this.url}/${dossierId}/ecritures`, { params });
  }

  /** RG2 à RG4 : correction d'une écriture non exportée. */
  modifier(dossierId: number, ecritureId: number, correction: CorrectionEcriture): Observable<EcritureJournal> {
    return this.http.put<EcritureJournal>(`${this.url}/${dossierId}/ecritures/${ecritureId}`, correction);
  }
}

const EUROS = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' });

export function euros(valeur: number): string {
  return EUROS.format(valeur);
}

/**
 * Montant saisi (« 1 234,50 », « 1234.5 ») -> nombre ; vide -> 0 ; null si ce n'est pas un montant
 * positif à 2 décimales au plus.
 */
export function lireMontant(saisie: string): number | null {
  const texte = saisie.replace(/\s/g, '').replace(',', '.');
  if (texte === '') {
    return 0;
  }
  return /^\d+(\.\d{1,2})?$/.test(texte) ? Number(texte) : null;
}

/** Montant affiché dans un champ de saisie (« 155,33 ») ; vide quand il est nul. */
export function saisieMontant(valeur: number): string {
  return valeur === 0 ? '' : valeur.toFixed(2).replace('.', ',');
}

/** Somme en centimes, pour éviter les arrondis des nombres à virgule. */
export function centimes(valeurs: number[]): number {
  return valeurs.reduce((total, v) => total + Math.round(v * 100), 0);
}

/** RG3, CA2 : « Écriture déséquilibrée : écart de 10,00 € » ; null si débit = crédit. */
export function messageDesequilibre(debits: number[], credits: number[]): string | null {
  const ecart = Math.abs(centimes(debits) - centimes(credits));
  return ecart === 0 ? null : `Écriture déséquilibrée : écart de ${euros(ecart / 100)}`;
}

/** Recherche dans le n° de pièce, le libellé ou un compte (sans majuscules ni accents), et filtre journal. */
export function filtrerEcritures(ecritures: EcritureJournal[], journal: string, recherche: string): EcritureJournal[] {
  const cherche = normaliser(recherche.trim());
  return ecritures.filter(
    (e) =>
      (journal === '' || e.journal === journal) &&
      (cherche === '' ||
        normaliser(e.piece ?? '').includes(cherche) ||
        normaliser(e.libelle).includes(cherche) ||
        e.lignes.some((l) => normaliser(l.numeroCompte).includes(cherche))),
  );
}

function normaliser(texte: string): string {
  return texte.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}
