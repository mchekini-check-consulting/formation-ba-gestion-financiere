import { apiBase } from './api';

describe('apiBase', () => {
  it('pointe la passerelle de l\'environnement du frontend', () => {
    expect(apiBase('frontend.formation-ba-gestion-financiere.qualif.check-consulting.net')).toBe(
      'https://gateway.formation-ba-gestion-financiere.qualif.check-consulting.net/backend',
    );
  });

  it('utilise le proxy du serveur de dev en local', () => {
    expect(apiBase('localhost')).toBe('/api');
  });
});
