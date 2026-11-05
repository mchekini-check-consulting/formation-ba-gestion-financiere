/**
 * US-36 RG1 : prix par dossier et par mois selon la tranche du cabinet. Le prix de la
 * tranche atteinte s'applique à tous les dossiers (300 dossiers × 7 € = 2 100 € HT / mois).
 * Partagé par la section Tarifs de la landing page et le formulaire « Créer un compte ».
 */
export interface Tier {
  name: string;
  range: string;
  /** Dernier dossier de la tranche. */
  upTo: number;
  price: number;
  highlighted?: boolean;
}

export const TIERS: Tier[] = [
  { name: 'Tranche 1', range: '1 à 50 dossiers', upTo: 50, price: 9 },
  { name: 'Tranche 2', range: '51 à 150 dossiers', upTo: 150, price: 8 },
  { name: 'Tranche 3', range: '151 à 300 dossiers', upTo: 300, price: 7, highlighted: true },
  { name: 'Tranche 4', range: 'Au-delà de 300', upTo: Infinity, price: 6 },
];

/** Mise en place, payée une seule fois (RG1). */
export const MISE_EN_PLACE_HT = 1500;

export function trancheDe(dossiers: number): Tier | undefined {
  return dossiers > 0 ? TIERS.find((tier) => dossiers <= tier.upTo) : undefined;
}

export function prixMensuel(dossiers: number): number {
  return dossiers * (trancheDe(dossiers)?.price ?? 0);
}

export function euros(amount: number): string {
  // L'espace fine de fr-FR est presque invisible : une espace insécable sépare les milliers.
  return amount.toLocaleString('fr-FR').replace(/ /g, ' ');
}
