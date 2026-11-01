import { apiBase } from './api';

describe('apiBase', () => {
  it("pointe la passerelle de l'environnement du frontend", () => {
    expect(
      apiBase('backend', 'frontend.formation-ba-gestion-financiere.qualif.check-consulting.net'),
    ).toBe('https://gateway.formation-ba-gestion-financiere.qualif.check-consulting.net/backend');
  });

  it('ajoute le nom du backend appelé', () => {
    expect(
      apiBase('cabinets', 'frontend.formation-ba-gestion-financiere.dev.check-consulting.net'),
    ).toBe('https://gateway.formation-ba-gestion-financiere.dev.check-consulting.net/cabinets');
  });

  it('utilise le proxy du serveur de dev en local', () => {
    expect(apiBase('backend', 'localhost')).toBe('/api/backend');
    expect(apiBase('cabinets', 'localhost')).toBe('/api/cabinets');
  });
});
