import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';
import { VueEcriture } from './facture.service';

/** US-58 : l'argent arrive (crédit) ou part (débit) du compte. */
export type SensTransaction = 'ENTREE' | 'SORTIE';

/** US-45 RG2 : filtre sens, « toutes » par défaut. */
export type FiltreSens = 'TOUTES' | SensTransaction;

/**
 * US-30 Opération validée : catégorie (US-41 : ACHAT, VENTE, FRAIS_BANCAIRES), compte, taux de TVA
 * et son écriture au journal de banque (BQ).
 */
export interface Validation {
  categorie: string;
  numeroCompte: string;
  tauxTva: number;
  valideePar: string;
  valideeLe: string;
  ecriture: VueEcriture | null;
}

/** US-30 Corps de la validation ; compte vide = compte par défaut de la catégorie. */
export interface DemandeValidation {
  categorie: string;
  numeroCompte: string | null;
  tauxTva: number;
}

/**
 * US-58 Transaction bancaire d'un dossier ; montant toujours positif, date « 2026-11-17 ».
 * US-30 : reference stable ; validation null tant que l'opération n'est pas validée.
 */
export interface Transaction {
  reference: string;
  date: string;
  libelle: string;
  tiers: string;
  montant: number;
  sens: SensTransaction;
  source: 'BOUCHON' | 'BANQUE';
  validation: Validation | null;
}

/** US-45 RG3 : totaux de la période. */
export interface Totaux {
  entrees: number;
  sorties: number;
  solde: number;
}

/** US-45 Transactions d'un dossier : appel au backend (via le gateway), déjà triées (RG4). */
@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase()}/dossiers`;

  lister(dossierId: number): Observable<Transaction[]> {
    return this.http.get<Transaction[]>(`${this.url}/${dossierId}/transactions`);
  }

  /** US-30 : valider l'opération et générer son écriture ; renvoie la ligne à jour. */
  valider(dossierId: number, reference: string, demande: DemandeValidation): Observable<Transaction> {
    return this.http.post<Transaction>(
      `${this.url}/${dossierId}/transactions/${encodeURIComponent(reference)}/validation`,
      demande,
    );
  }
}

/** US-30 : taux de TVA proposés à la validation d'une opération. */
export const TAUX_TVA = [20, 10, 5.5, 2.1, 0];

/** US-30 : catégorie proposée par défaut selon le sens (sortie : Achat, entrée : Vente). */
export function categorieParDefaut(sens: SensTransaction): string {
  return sens === 'SORTIE' ? 'ACHAT' : 'VENTE';
}

/** US-30 : taux proposé par défaut (les frais bancaires sont en général sans TVA). */
export function tauxParDefaut(categorie: string): number {
  return categorie === 'FRAIS_BANCAIRES' ? 0 : 20;
}

/** Mois « 2026-11 » de la date donnée, à l'heure de Paris (RG2 : mois en cours par défaut). */
export function moisCourant(maintenant: Date = new Date()): string {
  const parties = new Intl.DateTimeFormat('fr-FR', { timeZone: 'Europe/Paris', year: 'numeric', month: '2-digit' })
    .formatToParts(maintenant);
  const valeur = (type: string) => parties.find((p) => p.type === type)?.value ?? '';
  return `${valeur('year')}-${valeur('month')}`;
}

/** RG2 : transactions du mois « 2026-11 ». */
export function duMois(transactions: Transaction[], mois: string): Transaction[] {
  return transactions.filter((t) => t.date.startsWith(`${mois}-`));
}

/** RG2 : filtre sens et recherche dans le libellé (sans tenir compte des majuscules ni des accents). */
export function filtrer(transactions: Transaction[], sens: FiltreSens, recherche: string): Transaction[] {
  const cherche = normaliser(recherche.trim());
  return transactions.filter(
    (t) => (sens === 'TOUTES' || t.sens === sens) && (cherche === '' || normaliser(t.libelle).includes(cherche)),
  );
}

/** RG3 : total des entrées, des sorties et solde (entrées − sorties), calculés en centimes. */
export function totaux(transactions: Transaction[]): Totaux {
  let entrees = 0;
  let sorties = 0;
  for (const t of transactions) {
    const centimes = Math.round(t.montant * 100);
    if (t.sens === 'ENTREE') {
      entrees += centimes;
    } else {
      sorties += centimes;
    }
  }
  return { entrees: entrees / 100, sorties: sorties / 100, solde: (entrees - sorties) / 100 };
}

const EUROS = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' });

/** 2400 -> « 2 400,00 € » (espaces insécables du format français). */
export function formaterMontant(montant: number): string {
  return EUROS.format(montant);
}

/** « 2026-06-14 » -> « 14/06/2026 » (date sans heure : aucun décalage horaire possible). */
export function formaterDate(date: string): string {
  const [annee, mois, jour] = date.split('-');
  return `${jour}/${mois}/${annee}`;
}

function normaliser(texte: string): string {
  return texte.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}
