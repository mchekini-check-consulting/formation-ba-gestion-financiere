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

export function erreursFormulaire(f: Formulaire): Erreurs {
  const erreurs: Erreurs = {
    nomCabinet: texte(f.nomCabinet, 200),
    siren: !f.siren.trim()
      ? MESSAGES.obligatoire
      : /^\d{9}$/.test(f.siren.replace(SEPARATEURS, ''))
        ? undefined
        : MESSAGES.siren,
    prenom: texte(f.prenom, 100),
    nom: texte(f.nom, 100),
    email: !f.email.trim()
      ? MESSAGES.obligatoire
      : /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(f.email.trim()) && f.email.trim().length <= 254
        ? undefined
        : MESSAGES.email,
    mobile: !f.mobile.trim()
      ? MESSAGES.obligatoire
      : /^0[67]\d{8}$/.test(normaliserMobile(f.mobile))
        ? undefined
        : MESSAGES.mobile,
    motDePasse: !f.motDePasse
      ? MESSAGES.obligatoire
      : evaluerRegles(f.motDePasse).every((r) => r.respectee) && f.motDePasse.length <= 64
        ? undefined
        : MESSAGES.motDePasse,
    nombreDossiers:
      f.nombreDossiers === null
        ? MESSAGES.obligatoire
        : Number.isInteger(f.nombreDossiers) && f.nombreDossiers >= 1 && f.nombreDossiers <= DOSSIERS_MAX
          ? undefined
          : MESSAGES.dossiers,
    codeParrainage: f.codeParrainage.trim().length > 20 ? MESSAGES.parrainage : undefined,
    cguAcceptees: f.cguAcceptees ? undefined : MESSAGES.cgu,
  };
  return Object.fromEntries(Object.entries(erreurs).filter(([, message]) => message)) as Erreurs;
}
