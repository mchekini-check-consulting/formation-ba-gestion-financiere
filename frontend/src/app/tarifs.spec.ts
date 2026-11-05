import { MISE_EN_PLACE_HT, euros, prixMensuel, trancheDe } from './tarifs';

describe('Tarifs (US-36 RG1)', () => {
  it('le prix de la tranche atteinte s’applique à tous les dossiers', () => {
    expect(prixMensuel(50)).toBe(450);
    expect(prixMensuel(51)).toBe(408);
    expect(prixMensuel(120)).toBe(960); // CA3
    expect(prixMensuel(300)).toBe(2100);
    expect(prixMensuel(301)).toBe(1806);
  });

  it('aucune tranche sans dossier', () => {
    expect(trancheDe(0)).toBeUndefined();
    expect(prixMensuel(0)).toBe(0);
    expect(trancheDe(1000)?.price).toBe(6);
  });

  it('montants en euros avec une espace insécable entre les milliers', () => {
    expect(euros(2100)).toBe('2 100');
    expect(euros(MISE_EN_PLACE_HT)).toBe('1 500');
  });
});
