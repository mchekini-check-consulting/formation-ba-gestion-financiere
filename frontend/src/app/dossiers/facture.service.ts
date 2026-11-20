import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';

/** US-12 RG1 : factures fournisseurs (achats) ou factures clients (ventes). */
export type Sens = 'ACHAT' | 'VENTE';

/** US-52 RG4 : déposée par le dirigeant (US-15, US-38) ou importée par le cabinet. */
export type Source = 'DEPOT_CLIENT' | 'IMPORT_CABINET';

export const SOURCES: Record<Source, string> = {
  DEPOT_CLIENT: 'Dépôt client',
  IMPORT_CABINET: 'Import cabinet',
};

/** US-51 RG1 : statut de traitement d'une facture. */
export type Statut = 'RECUE' | 'EN_COURS' | 'TRAITEE' | 'A_COMPLETER';

export const STATUTS: Statut[] = ['RECUE', 'EN_COURS', 'TRAITEE', 'A_COMPLETER'];

export const LIBELLES_STATUT: Record<Statut, string> = {
  RECUE: 'Reçue',
  EN_COURS: 'En cours de traitement',
  TRAITEE: 'Traitée',
  A_COMPLETER: 'À compléter',
};

/** US-51 RG4 : pastille de couleur de chaque statut (classe CSS). */
export const PASTILLES_STATUT: Record<Statut, string> = {
  RECUE: 'pastille--recue',
  EN_COURS: 'pastille--en-cours',
  TRAITEE: 'pastille--traitee',
  A_COMPLETER: 'pastille--a-completer',
};

/** US-51 CA3. */
export const MESSAGE_MOTIF_MANQUANT = 'Indiquez le motif';

/**
 * US-12 RG2 : une ligne de la liste ; RG3 : nouvelle tant que personne du cabinet ne l'a ouverte.
 * US-51 : motif de « À compléter » (RG3) et seuls statuts proposés depuis le statut actuel (RG2, CA4).
 * US-52 RG4 : source (dépôt du dirigeant ou import du cabinet).
 */
export interface LigneFacture {
  id: number;
  nomFichier: string;
  deposeeLe: string;
  deposeePar: string;
  statut: Statut;
  nouvelle: boolean;
  motif: string | null;
  statutsPossibles: Statut[];
  source: Source;
}

/** US-13 : en-tête de la page d'aperçu. */
export interface VueFacture {
  id: number;
  nomFichier: string;
  typeFichier: string;
  sens: Sens;
}

/** exemplesPossibles : mode test seulement (dev, qualif), jamais en preprod ni en prod. */
export interface ListeFactures {
  factures: LigneFacture[];
  exemplesPossibles: boolean;
}

/** US-12 Factures d'un dossier : appels au backend (via le gateway). */
@Injectable({ providedIn: 'root' })
export class FactureService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase()}/dossiers`;

  lister(dossierId: number, sens: Sens): Observable<ListeFactures> {
    return this.http.get<ListeFactures>(`${this.url}/${dossierId}/factures`, {
      params: new HttpParams().set('sens', sens),
    });
  }

  /** US-52 : un fichier par appel, importé dans l'onglet (RG1) ; un refus n'empêche pas les autres (RG2). */
  importer(dossierId: number, sens: Sens, fichier: File): Observable<LigneFacture> {
    const corps = new FormData();
    corps.append('fichier', fichier, fichier.name);
    return this.http.post<LigneFacture>(`${this.url}/${dossierId}/factures`, corps, {
      params: new HttpParams().set('sens', sens),
    });
  }

  /** US-13 : nom et type du fichier, pour la page d'aperçu. */
  consulter(dossierId: number | string, factureId: number | string): Observable<VueFacture> {
    return this.http.get<VueFacture>(`${this.url}/${dossierId}/factures/${factureId}`);
  }

  /** US-13 RG1 : fichier pour l'aperçu ; RG2 : fichier d'origine à télécharger. */
  fichier(dossierId: number | string, factureId: number | string, telechargement: boolean): Observable<Blob> {
    const params = telechargement ? new HttpParams().set('telechargement', 'true') : undefined;
    return this.http.get(`${this.url}/${dossierId}/factures/${factureId}/fichier`, { params, responseType: 'blob' });
  }

  /** US-51 : changer le statut ; le motif n'est utile que pour « À compléter » (RG3). */
  changerStatut(dossierId: number, factureId: number, statut: Statut, motif: string | null): Observable<LigneFacture> {
    return this.http.patch<LigneFacture>(`${this.url}/${dossierId}/factures/${factureId}/statut`, { statut, motif });
  }

  /** Mode test : factures d'exemple, le dépôt (US-15) n'existant pas encore. */
  ajouterExemples(dossierId: number): Observable<void> {
    return this.http.post<void>(`${this.url}/${dossierId}/factures/exemples`, null);
  }
}

/** « 2026-11-08T09:05:00Z » -> « 08/11/2026 à 10:05 » (heure de Paris). */
export function formaterDepot(date: string): string {
  const d = new Date(date);
  const jour = d.toLocaleDateString('fr-FR', { timeZone: 'Europe/Paris' });
  const heure = d.toLocaleTimeString('fr-FR', { timeZone: 'Europe/Paris', hour: '2-digit', minute: '2-digit' });
  return `${jour} à ${heure}`;
}
