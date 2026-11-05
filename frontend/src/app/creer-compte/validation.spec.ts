import { Formulaire, MESSAGES, erreursFormulaire, normaliserMobile } from './validation';

/** CA1 : formulaire de Claire Martin pour Cabinet Test. */
function claire(): Formulaire {
  return {
    nomCabinet: 'Cabinet Test',
    siren: '123 456 789',
    prenom: 'Claire',
    nom: 'Martin',
    email: 'claire.martin@cabinet-test.fr',
    mobile: '06 12 34 56 78',
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
      mobile: '',
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
      mobile: MESSAGES.obligatoire,
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
        mobile: '01 23 45 67 89',
        motDePasse: 'faible',
        nombreDossiers: 0,
        codeParrainage: 'X'.repeat(21),
      }),
    ).toEqual({
      nomCabinet: MESSAGES.tropLong,
      siren: MESSAGES.siren,
      prenom: MESSAGES.tropLong,
      email: MESSAGES.email,
      mobile: MESSAGES.mobile,
      motDePasse: MESSAGES.motDePasse,
      nombreDossiers: MESSAGES.dossiers,
      codeParrainage: MESSAGES.parrainage,
    });
    expect(erreursFormulaire({ ...claire(), nombreDossiers: 2.5 }).nombreDossiers).toBe(MESSAGES.dossiers);
  });

  it('formats de mobile acceptés', () => {
    expect(normaliserMobile('+33 6 12 34 56 78')).toBe('0612345678');
    expect(normaliserMobile('0033 7 12 34 56 78')).toBe('0712345678');
    expect(normaliserMobile('06.12.34.56.78')).toBe('0612345678');
    expect(erreursFormulaire({ ...claire(), mobile: '+33 6 12 34 56 78' })).toEqual({});
  });
});
