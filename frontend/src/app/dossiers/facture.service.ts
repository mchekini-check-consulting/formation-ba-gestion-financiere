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
  /** US-53 : statut de la lecture automatique ; null pour une facture jamais lue (facture client…). */
  lecture: StatutLecture | null;
  /** US-53 : pastille « À vérifier » (au moins un champ lu avec un doute ou non lu). */
  lectureAVerifier: boolean;
}

/** US-53 RG6 : statut de la lecture automatique d'une facture fournisseur. */
export type StatutLecture = 'EN_COURS' | 'LUE' | 'ECHOUEE';

export const LIBELLES_LECTURE: Record<StatutLecture, string> = {
  EN_COURS: 'Lecture en cours',
  LUE: 'Lue',
  ECHOUEE: 'Lecture échouée',
};

export const PASTILLES_LECTURE: Record<StatutLecture, string> = {
  EN_COURS: 'pastille--lecture-en-cours',
  LUE: 'pastille--lue',
  ECHOUEE: 'pastille--lecture-echouee',
};

/** US-53 RG2 : champs lus sur une facture fournisseur. */
export type ChampFacture =
  | 'TYPE_TIERS'
  | 'NOM_TIERS'
  | 'NUMERO_FACTURE'
  | 'DATE_EMISSION'
  | 'MONTANT_HT'
  | 'MONTANT_TVA'
  | 'TAUX_TVA'
  | 'MONTANT_TTC'
  | 'COMPTE_CONTREPARTIE';

export const CHAMPS: ChampFacture[] = [
  'TYPE_TIERS',
  'NOM_TIERS',
  'NUMERO_FACTURE',
  'DATE_EMISSION',
  'MONTANT_HT',
  'MONTANT_TVA',
  'TAUX_TVA',
  'MONTANT_TTC',
  'COMPTE_CONTREPARTIE',
];

export const LIBELLES_CHAMP: Record<ChampFacture, string> = {
  TYPE_TIERS: 'Type de tiers',
  NOM_TIERS: 'Nom du tiers',
  NUMERO_FACTURE: 'Numéro de facture',
  DATE_EMISSION: "Date d'émission",
  MONTANT_HT: 'Montant HT',
  MONTANT_TVA: 'Montant TVA',
  TAUX_TVA: 'Taux de TVA',
  MONTANT_TTC: 'Montant TTC',
  COMPTE_CONTREPARTIE: 'Compte de contrepartie',
};

/** US-54 CA2 : TVA lue pour un taux (base HT absente si la facture ne la donne pas). */
export interface TvaParTaux {
  taux: number;
  baseHt: number | null;
  montantTva: number;
}

/**
 * US-53 RG2 à RG6 : résultat de la lecture. aVerifier : champs lus avec un doute (RG4, RG5) ;
 * aRenseigner : champs non lus (RG3). Champs vides tant que la lecture est en cours ou si elle a échoué.
 * US-54 : facture client (type de tiers « Client ») ; CA2 plusieurs taux : « Multiple » et détail par taux.
 */
export interface Lecture {
  statut: StatutLecture;
  typeTiers: 'FOURNISSEUR' | 'CLIENT' | null;
  nomTiers: string | null;
  numeroFacture: string | null;
  dateEmission: string | null;
  montantHt: number | null;
  montantTva: number | null;
  tauxTva: number | null;
  tauxMultiple: boolean;
  detailTva: TvaParTaux[];
  montantTtc: number | null;
  compteContrepartie: string | null;
  libelleContrepartie: string | null;
  aVerifier: ChampFacture[];
  aRenseigner: ChampFacture[];
}

/**
 * US-13 : en-tête de la page d'aperçu ; US-53 : résultat de la lecture (null : jamais lue).
 * US-55 : statut de traitement (une facture « Traitée » ne se modifie plus) et dernière modification (RG8).
 */
export interface VueFacture {
  id: number;
  nomFichier: string;
  typeFichier: string;
  sens: Sens;
  statut: Statut;
  lecture: Lecture | null;
  modification: { par: string; le: string } | null;
}

/**
 * US-55 RG1, RG2 : informations saisies ou corrigées sur la fiche ; null = non renseigné.
 * dateEmission : « 2026-06-12 » ; compteContrepartie : numéro d'un compte du plan du dossier (RG3).
 */
export interface InformationsFacture {
  typeTiers: 'FOURNISSEUR' | 'CLIENT' | null;
  nomTiers: string | null;
  numeroFacture: string | null;
  dateEmission: string | null;
  montantHt: number | null;
  montantTva: number | null;
  tauxTva: number | null;
  montantTtc: number | null;
  compteContrepartie: string | null;
}

export interface ListeFactures {
  factures: LigneFacture[];
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

  /** US-55 RG2 : enregistrer les informations corrigées, sans changer le statut. */
  enregistrerInformations(
    dossierId: number | string,
    factureId: number,
    informations: InformationsFacture,
  ): Observable<VueFacture> {
    return this.http.put<VueFacture>(`${this.url}/${dossierId}/factures/${factureId}/informations`, informations);
  }

  /** US-55 RG4 à RG6 : enregistrer puis passer la facture en « Traitée » (refus 400 : message à afficher). */
  valider(dossierId: number | string, factureId: number, informations: InformationsFacture): Observable<VueFacture> {
    return this.http.post<VueFacture>(`${this.url}/${dossierId}/factures/${factureId}/validation`, informations);
  }

  /** US-51 : changer le statut ; le motif n'est utile que pour « À compléter » (RG3). */
  changerStatut(dossierId: number, factureId: number, statut: Statut, motif: string | null): Observable<LigneFacture> {
    return this.http.patch<LigneFacture>(`${this.url}/${dossierId}/factures/${factureId}/statut`, { statut, motif });
  }
}

/** « 2026-11-08T09:05:00Z » -> « 08/11/2026 à 10:05 » (heure de Paris). */
export function formaterDepot(date: string): string {
  const d = new Date(date);
  const jour = d.toLocaleDateString('fr-FR', { timeZone: 'Europe/Paris' });
  const heure = d.toLocaleTimeString('fr-FR', { timeZone: 'Europe/Paris', hour: '2-digit', minute: '2-digit' });
  return `${jour} à ${heure}`;
}

const EUROS = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' });

/** US-53 : valeur d'un champ lu, prête à afficher ; null si le champ n'a pas été lu (« À renseigner »). */
export function valeurChamp(lecture: Lecture, champ: ChampFacture): string | null {
  switch (champ) {
    case 'TYPE_TIERS':
      return typeTiers(lecture);
    case 'NOM_TIERS':
      return lecture.nomTiers;
    case 'NUMERO_FACTURE':
      return lecture.numeroFacture;
    case 'DATE_EMISSION':
      return lecture.dateEmission === null ? null : lecture.dateEmission.split('-').reverse().join('/');
    case 'MONTANT_HT':
      return montant(lecture.montantHt);
    case 'MONTANT_TVA':
      return montant(lecture.montantTva);
    case 'TAUX_TVA':
      return tauxLu(lecture);
    case 'MONTANT_TTC':
      return montant(lecture.montantTtc);
    case 'COMPTE_CONTREPARTIE':
      return compteLu(lecture);
  }
}

function typeTiers(lecture: Lecture): string | null {
  if (lecture.typeTiers === null) {
    return null;
  }
  return lecture.typeTiers === 'CLIENT' ? 'Client' : 'Fournisseur';
}

/** US-54 CA2 : « Multiple » quand la facture a plusieurs taux de TVA. */
function tauxLu(lecture: Lecture): string | null {
  if (lecture.tauxMultiple) {
    return 'Multiple';
  }
  return lecture.tauxTva === null ? null : taux(lecture.tauxTva);
}

function compteLu(lecture: Lecture): string | null {
  if (lecture.compteContrepartie === null) {
    return null;
  }
  return lecture.libelleContrepartie
    ? `${lecture.compteContrepartie} – ${lecture.libelleContrepartie}`
    : lecture.compteContrepartie;
}

function montant(valeur: number | null): string | null {
  return valeur === null ? null : EUROS.format(valeur);
}

/** « 5.5 » -> « 5,5 % ». */
export function taux(valeur: number): string {
  return `${valeur.toLocaleString('fr-FR')} %`;
}

/** US-54 CA2 : montant d'une ligne du détail de la TVA ; « — » si la facture ne le donne pas. */
export function montantOuTiret(valeur: number | null): string {
  return montant(valeur) ?? '—';
}
