// Textes de la landing page (repris de la maquette « Landing page »)

export interface Tuile {
  titre: string;
  texte: string;
  bientot: boolean;
}

export const TUILES_CABINETS: Tuile[] = [
  {
    titre: 'Synchronisation bancaire DSP2',
    texte:
      'Les comptes de vos clients lus par API chaque nuit, et en quelques minutes avec « Actualiser ».',
    bientot: true,
  },
  {
    titre: 'Catégorisation et rapprochement',
    texte: 'Chaque opération classée selon vos règles, reliée à sa facture, puis lettrée.',
    bientot: true,
  },
  {
    titre: 'Écritures et export FEC',
    texte:
      'Les écritures passées sur le plan comptable du dossier, contrôlées, puis exportées vers votre logiciel.',
    bientot: true,
  },
  {
    titre: 'Préparation de la TVA',
    texte: 'TVA collectée et déductible calculée par taux, prête à déclarer.',
    bientot: true,
  },
  {
    titre: 'Rapports financiers',
    texte:
      'Compte de résultat, évolution sur 12 mois et répartition des charges, exportables en PDF.',
    bientot: true,
  },
  {
    titre: 'Dossiers clients et équipe',
    texte:
      'Un dossier par entreprise, créé à partir du SIREN, et chaque collaborateur avec son rôle.',
    bientot: false,
  },
];

export const TUILES_ENTREPRISES: Tuile[] = [
  {
    titre: 'Déposer factures et notes de frais',
    texte: 'Glissez vos PDF ou photos ; vos tickets de notes de frais seront lus automatiquement.',
    bientot: false,
  },
  {
    titre: 'Connecter sa banque',
    texte:
      'Autorisez la lecture de vos comptes, en lecture seule, sans jamais donner vos identifiants.',
    bientot: true,
  },
  {
    titre: 'Trésorerie en temps réel',
    texte: 'Votre solde à jour et ce qui doit entrer et sortir dans les 30 prochains jours.',
    bientot: true,
  },
  {
    titre: 'Échanges sécurisés avec le cabinet',
    texte: 'Un seul espace pour tout transmettre, même si vous dirigez plusieurs sociétés.',
    bientot: false,
  },
];

export const ETAPES = [
  {
    titre: 'Créez le dossier',
    texte: "Saisissez le SIREN : la fiche de l'entreprise se remplit toute seule.",
  },
  {
    titre: 'Invitez votre client',
    texte: 'Il reçoit un email, choisit son mot de passe et accède à son espace.',
  },
  {
    titre: 'Recevez les factures',
    texte: "Ses factures d'achat et de vente arrivent rangées dans son dossier.",
  },
];

export const MODULES = [
  {
    titre: 'Synchronisation bancaire',
    texte: 'Les opérations de vos clients chaque matin, en lecture seule.',
  },
  {
    titre: 'Rapprochement automatique',
    texte: 'Pointéo propose la facture de chaque opération, vous validez.',
  },
  { titre: 'CRM', texte: 'Vos échanges et missions avec chaque client.' },
  { titre: 'Suivi des temps', texte: 'Le temps passé par dossier et par collaborateur.' },
];

export const INCLUS = [
  'Collaborateurs illimités, avec leurs rôles',
  'Espace client gratuit pour chaque entreprise suivie',
  "Dépôt des factures, hébergement dans l'Union européenne",
  'Mise en place : 1 500 € HT une seule fois, sans engagement',
];

export const FAQ = [
  {
    question: 'Chaque collaborateur a-t-il son propre accès ?',
    reponse:
      'Oui. L’administrateur invite chaque collaborateur avec un rôle : administrateur, associé ou comptable. Un comptable ne voit que les dossiers qui lui sont attribués.',
  },
  {
    question: 'Comment mon client accède-t-il à son espace ?',
    reponse:
      'Depuis son dossier, vous l’invitez par email. Il choisit son mot de passe et accède à son espace de dépôt.',
  },
  {
    question: 'Quels fichiers mes clients peuvent-ils déposer ?',
    reponse: 'Des PDF, JPG et PNG, jusqu’à 10 Mo par fichier et 20 fichiers par dépôt.',
  },
  {
    question: 'Où sont stockées les factures ?',
    reponse:
      'Dans l’Union européenne, chiffrées, et conservées 10 ans, la durée légale des pièces comptables.',
  },
  {
    question: 'Comment sont protégées mes données ?',
    reponse:
      'Chaque cabinet ne voit que ses dossiers, chaque client que son entreprise, et chaque connexion est confirmée par un code reçu par SMS.',
  },
  {
    question: 'Quand arrivent les modules CRM et Suivi des temps ?',
    reponse:
      'Ils sont visibles dans chaque dossier avec la mention « Bientôt disponible » et seront activés dans une prochaine version.',
  },
  {
    question: 'Pointéo remplace-t-il notre logiciel comptable ?',
    reponse:
      'Non. Pointéo le complète : il prépare les écritures et les exporte au format FEC, que vous importez dans votre logiciel.',
  },
];
