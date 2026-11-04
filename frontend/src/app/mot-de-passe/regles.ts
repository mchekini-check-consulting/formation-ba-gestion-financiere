/**
 * US-06 RG3 : règles du nouveau mot de passe. Mêmes libellés que le backend
 * (backend/.../compte/RegleMotDePasse.java), qui les vérifie à nouveau.
 */
export interface RegleEvaluee {
  libelle: string;
  respectee: boolean;
}

const REGLES: { libelle: string; test: (valeur: string) => boolean }[] = [
  { libelle: '12 caractères minimum', test: (v) => v.length >= 12 },
  { libelle: 'Une majuscule', test: (v) => /\p{Lu}/u.test(v) },
  { libelle: 'Une minuscule', test: (v) => /\p{Ll}/u.test(v) },
  { libelle: 'Un chiffre', test: (v) => /\p{Nd}/u.test(v) },
  { libelle: 'Un caractère spécial', test: (v) => /[^\p{L}\p{N}]/u.test(v) },
];

export function evaluerRegles(motDePasse: string): RegleEvaluee[] {
  return REGLES.map((r) => ({ libelle: r.libelle, respectee: r.test(motDePasse) }));
}
