import { centimes, lireMontant, messageDesequilibre, saisieMontant } from './ecriture.service';

describe('US-47 Montants d’une écriture', () => {
  it('lecture des montants saisis', () => {
    expect(lireMontant('')).toBe(0);
    expect(lireMontant(' 1 234,50 ')).toBe(1234.5);
    expect(lireMontant('155.33')).toBe(155.33);
    expect(lireMontant('155,333')).toBeNull();
    expect(lireMontant('-1')).toBeNull();
    expect(lireMontant('abc')).toBeNull();
  });

  it('montant affiché dans un champ', () => {
    expect(saisieMontant(0)).toBe('');
    expect(saisieMontant(186.4)).toBe('186,40');
  });

  it('RG3, CA2 : écart en centimes, sans erreur d’arrondi', () => {
    expect(centimes([0.1, 0.2])).toBe(30);
    expect(messageDesequilibre([155.33, 31.07], [186.4])).toBeNull();
    expect(messageDesequilibre([165.33, 31.07], [186.4])?.replace(/\s/g, ' ')).toBe(
      'Écriture déséquilibrée : écart de 10,00 €',
    );
  });
});
