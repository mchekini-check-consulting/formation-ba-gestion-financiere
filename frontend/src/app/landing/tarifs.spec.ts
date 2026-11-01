import { simuler } from './tarifs';

describe('simuler', () => {
  it('reprend l’exemple de la maquette : 300 dossiers = 2 300 €', () => {
    const s = simuler(300);
    expect(s.lignes).toEqual([
      { dossiers: 50, prix: 9, montant: 450 },
      { dossiers: 100, prix: 8, montant: 800 },
      { dossiers: 150, prix: 7, montant: 1050 },
    ]);
    expect(s.total).toBe(2300);
    expect(s.moyenne).toBeCloseTo(7.67, 2);
  });

  it('reste dans la première tranche jusqu’à 50 dossiers', () => {
    expect(simuler(50)).toEqual({
      lignes: [{ dossiers: 50, prix: 9, montant: 450 }],
      total: 450,
      moyenne: 9,
    });
  });

  it('applique 6 € au-delà de 300 dossiers', () => {
    const s = simuler(310);
    expect(s.lignes.at(-1)).toEqual({ dossiers: 10, prix: 6, montant: 60 });
    expect(s.total).toBe(2360);
  });

  it('ne compte rien pour une saisie vide, négative ou invalide', () => {
    for (const n of [0, -5, Number.NaN]) {
      expect(simuler(n)).toEqual({ lignes: [], total: 0, moyenne: 0 });
    }
  });

  it('ignore les décimales', () => {
    expect(simuler(10.9).total).toBe(90);
  });
});
