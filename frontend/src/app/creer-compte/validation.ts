import { evaluerRegles } from '../mot-de-passe/regles';

/**
 * US-36 RG2 : champs du formulaire « Créer un compte ». Mêmes messages que le backend
 * (backend/.../cabinet/ValidationInscription.java), qui refait les mêmes vérifications.
 */
export interface Formulaire {
  nomCabinet: string;
  siren: string;
  prenom: string;
  nom: string;
  email: string;
  mobile: string;
  motDePasse: string;
  nombreDossiers: number | null;
  codeParrainage: string;
  cguAcceptees: boolean;
}

export type Champ = keyof Formulaire;
export type Erreurs = Partial<Record<Champ, string>>;

export const MESSAGES = {
  obligatoire: 'Ce champ est obligatoire',
  tropLong: 'Ce champ est trop long',
  siren: 'Le SIREN comporte 9 chiffres',
  email: 'Adresse email invalide',
  mobile: 'Numéro de mobile invalide (ex. 06 12 34 56 78)',
  motDePasse: 'Le mot de passe ne respecte pas toutes les règles',
  dossiers: 'Indiquez au moins 1 dossier',
  parrainage: 'Code de parrainage invalide',
  cgu: 'Vous devez accepter les CGU pour créer votre compte',
} as const;

const DOSSIERS_MAX = 100_000;
const SEPARATEURS = /[\s.-]/g;

/** « +33 6 12 34 56 78 » ou « 06.12.34.56.78 » -> « 0612345678 ». */
export function normaliserMobile(saisie: string): string {
  const chiffres = saisie.replace(SEPARATEURS, '');
  if (chiffres.startsWith('+33')) {
    return '0' + chiffres.slice(3);
  }
  if (chiffres.startsWith('0033')) {
    return '0' + chiffres.slice(4);
  }
  return chiffres;
}

function texte(valeur: string, longueurMax: number): string | undefined {
  if (!valeur.trim()) {
    return MESSAGES.obligatoire;
  }
  return valeur.trim().length > longueurMax ? MESSAGES.tropLong : undefined;
}

/** Champ obligatoire : vide -> « obligatoire », sinon le message si la valeur n'est pas valide. */
function verifier(saisi: boolean, valide: () => boolean, message: string): string | undefined {
  if (!saisi) {
    return MESSAGES.obligatoire;
  }
  return valide() ? undefined : message;
}

function sirenValide(siren: string): boolean {
  return /^\d{9}$/.test(siren.replace(SEPARATEURS, ''));
}

function emailValide(email: string): boolean {
  const valeur = email.trim();
  return valeur.length <= 254 && /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(valeur);
}

function motDePasseValide(motDePasse: string): boolean {
  return motDePasse.length <= 64 && evaluerRegles(motDePasse).every((r) => r.respectee);
}

function dossiersValides(dossiers: number | null): boolean {
  return Number.isInteger(dossiers) && (dossiers ?? 0) >= 1 && (dossiers ?? 0) <= DOSSIERS_MAX;
}

export function erreursFormulaire(f: Formulaire): Erreurs {
  const erreurs: Erreurs = {
    nomCabinet: texte(f.nomCabinet, 200),
    siren: verifier(!!f.siren.trim(), () => sirenValide(f.siren), MESSAGES.siren),
    prenom: texte(f.prenom, 100),
    nom: texte(f.nom, 100),
    email: verifier(!!f.email.trim(), () => emailValide(f.email), MESSAGES.email),
    mobile: verifier(!!f.mobile.trim(), () => /^0[67]\d{8}$/.test(normaliserMobile(f.mobile)), MESSAGES.mobile),
    motDePasse: verifier(!!f.motDePasse, () => motDePasseValide(f.motDePasse), MESSAGES.motDePasse),
    nombreDossiers: verifier(f.nombreDossiers !== null, () => dossiersValides(f.nombreDossiers), MESSAGES.dossiers),
    codeParrainage: f.codeParrainage.trim().length > 20 ? MESSAGES.parrainage : undefined,
    cguAcceptees: f.cguAcceptees ? undefined : MESSAGES.cgu,
  };
  return Object.fromEntries(Object.entries(erreurs).filter(([, message]) => message)) as Erreurs;
}
