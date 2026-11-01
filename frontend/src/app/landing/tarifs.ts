// Grille tarifaire par tranches : chaque prix ne s'applique qu'aux dossiers de sa tranche.
export interface Tranche {
  numero: number;
  libelle: string;
  /** Dernier dossier de la tranche (null = pas de limite) */
  jusqua: number | null;
  prix: number;
}

export const TRANCHES: Tranche[] = [
  { numero: 1, libelle: '1 à 50 dossiers', jusqua: 50, prix: 9 },
  { numero: 2, libelle: '51 à 150 dossiers', jusqua: 150, prix: 8 },
  { numero: 3, libelle: '151 à 300 dossiers', jusqua: 300, prix: 7 },
  { numero: 4, libelle: 'Au-delà de 300', jusqua: null, prix: 6 },
];

export interface LigneSimulation {
  dossiers: number;
  prix: number;
  montant: number;
}

export interface Simulation {
  lignes: LigneSimulation[];
  total: number;
  /** Prix moyen par dossier, 0 si aucun dossier */
  moyenne: number;
}

export function simuler(nombreDossiers: number): Simulation {
  const n = Number.isFinite(nombreDossiers) ? Math.max(0, Math.floor(nombreDossiers)) : 0;
  const lignes: LigneSimulation[] = [];
  let debut = 0;
  for (const tranche of TRANCHES) {
    const fin = tranche.jusqua ?? Number.POSITIVE_INFINITY;
    const dossiers = Math.min(n, fin) - debut;
    if (dossiers <= 0) break;
    lignes.push({ dossiers, prix: tranche.prix, montant: dossiers * tranche.prix });
    debut = fin;
  }
  const total = lignes.reduce((somme, l) => somme + l.montant, 0);
  return { lignes, total, moyenne: n === 0 ? 0 : total / n };
}
