import { Formulaire, MESSAGES, erreursFormulaire } from './validation';

/** CA1 : formulaire de Claire Martin pour Cabinet Test. */
function claire(): Formulaire {
  return {
    nomCabinet: 'Cabinet Test',
    siren: '123 456 789',
    prenom: 'Claire',
    nom: 'Martin',
    email: 'claire.martin@cabinet-test.fr',
    motDePasse: 'Pointeo2026!!',
    nombreDossiers: 120,
    codeParrainage: '',
    cguAcceptees: true,
  };
}

describe('Validation du formulaire « Créer un compte » (US-36 RG2)', () => {
  it('formulaire complet : aucune erreur', () => {
    expect(erreursFormulaire(claire())).toEqual({});
  });

  it('champs obligatoires et CGU', () => {
    const vide: Formulaire = {
      nomCabinet: ' ',
      siren: '',
      prenom: '',
      nom: '',
      email: '',
      motDePasse: '',
      nombreDossiers: null,
      codeParrainage: '',
      cguAcceptees: false,
    };
    expect(erreursFormulaire(vide)).toEqual({
      nomCabinet: MESSAGES.obligatoire,
      siren: MESSAGES.obligatoire,
      prenom: MESSAGES.obligatoire,
      nom: MESSAGES.obligatoire,
      email: MESSAGES.obligatoire,
      motDePasse: MESSAGES.obligatoire,
      nombreDossiers: MESSAGES.obligatoire,
      cguAcceptees: MESSAGES.cgu,
    });
  });

  it('valeurs invalides', () => {
    expect(
      erreursFormulaire({
        ...claire(),
        nomCabinet: 'C'.repeat(201),
        siren: '12345678',
        prenom: 'P'.repeat(101),
        email: 'claire.martin',
        motDePasse: 'faible',
        nombreDossiers: 0,
        codeParrainage: 'X'.repeat(21),
      }),
    ).toEqual({
      nomCabinet: MESSAGES.tropLong,
      siren: MESSAGES.siren,
      prenom: MESSAGES.tropLong,
      email: MESSAGES.email,
      motDePasse: MESSAGES.motDePasse,
      nombreDossiers: MESSAGES.dossiers,
      codeParrainage: MESSAGES.parrainage,
    });
    expect(erreursFormulaire({ ...claire(), nombreDossiers: 2.5 }).nombreDossiers).toBe(MESSAGES.dossiers);
  });

  it('SIREN saisi avec des espaces ou des points : accepté', () => {
    expect(erreursFormulaire({ ...claire(), siren: '123.456.789' })).toEqual({});
  });
});
