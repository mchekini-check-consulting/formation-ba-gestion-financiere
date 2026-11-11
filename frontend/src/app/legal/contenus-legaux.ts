/**
 * Textes des pages légales du pied de page (US-34, PTO-59).
 * Prototype de formation : l'éditeur, l'hébergeur et les contacts sont fictifs.
 * RG3 : ces CGU sont celles acceptées à l'activation (US-08) et à la création de compte (US-36) ;
 * toute nouvelle version change `version` et `date`.
 */

export interface RubriqueLegale {
  titre: string;
  texte: string;
}

export interface PageLegaleContenu {
  titre: string;
  /** Ligne sous le titre : date de mise à jour, ou version et date pour les CGU (RG2). */
  sousTitre: string;
  rubriques: RubriqueLegale[];
}

export type PageLegaleId = 'mentions-legales' | 'cgu' | 'confidentialite';

export const VERSION_CGU = { version: '1.0', date: '28/10/2026' };

export const PAGES_LEGALES: Record<PageLegaleId, PageLegaleContenu> = {
  'mentions-legales': {
    titre: 'Mentions légales',
    sousTitre: 'Dernière mise à jour : 28/10/2026',
    rubriques: [
      {
        titre: 'Éditeur du site',
        texte:
          'Pointéo SAS (société fictive, prototype de formation), au capital de 10 000 €, ' +
          '12 rue des Comptables, 75009 Paris. SIREN 000 000 000. ' +
          'Directeur de la publication : le président de Pointéo SAS.',
      },
      {
        titre: 'Hébergement',
        texte:
          'Nuage Europe SAS (hébergeur fictif), 1 avenue du Cloud, 69000 Lyon. ' +
          "Toutes les données sont hébergées dans l'Union européenne.",
      },
      {
        titre: 'Contact',
        texte:
          'Pour toute question sur le site : contact@pointeo.example ou par courrier à ' +
          "l'adresse de l'éditeur.",
      },
      {
        titre: 'Propriété intellectuelle',
        texte: 'Les contenus du site (textes, logo, visuels) ne peuvent pas être réutilisés sans accord.',
      },
    ],
  },
  cgu: {
    titre: "Conditions générales d'utilisation",
    sousTitre: `Version ${VERSION_CGU.version} du ${VERSION_CGU.date} · acceptées à l'activation du compte`,
    rubriques: [
      {
        titre: 'Objet',
        texte: "Accès à Pointéo, logiciel web pour les cabinets d'expertise comptable et leurs clients.",
      },
      {
        titre: 'Comptes et accès',
        texte:
          "Le compte du cabinet est créé à son inscription ; les collaborateurs et les dirigeants sont invités par le cabinet. " +
          'Les identifiants sont personnels ; un code de vérification est envoyé par email à la connexion.',
      },
      {
        titre: 'Données et confidentialité',
        texte:
          "Chaque cabinet ne voit que ses dossiers ; fichiers chiffrés et hébergés dans l'Union européenne.",
      },
      {
        titre: 'Accès bancaire',
        texte: 'Lecture seule ; aucun paiement possible depuis Pointéo ; accord retirable à tout moment.',
      },
      {
        titre: 'Responsabilités',
        texte:
          'Pointéo propose, le comptable valide ; Pointéo complète le logiciel comptable sans le remplacer.',
      },
    ],
  },
  confidentialite: {
    titre: 'Politique de confidentialité',
    sousTitre: 'Dernière mise à jour : 28/10/2026',
    rubriques: [
      {
        titre: 'Responsable du traitement',
        texte: "Pointéo SAS, éditeur du site (voir les mentions légales).",
      },
      {
        titre: 'Données collectées',
        texte:
          "Identité et coordonnées professionnelles des utilisateurs, informations du cabinet, " +
          'dossiers clients, factures et relevés bancaires transmis par le cabinet ou ses clients.',
      },
      {
        titre: 'Finalités',
        texte:
          "Fournir le service (comptes, dossiers, factures, accès bancaire en lecture seule), " +
          'sécuriser les connexions et répondre aux demandes de contact. Aucune revente de données.',
      },
      {
        titre: 'Durée de conservation',
        texte:
          "Pendant la durée du contrat du cabinet, puis le temps imposé par les obligations légales " +
          '(notamment 10 ans pour les pièces comptables).',
      },
      {
        titre: 'Hébergement et sécurité',
        texte: "Données chiffrées et hébergées dans l'Union européenne ; accès limité au cabinet concerné.",
      },
      {
        titre: 'Vos droits',
        texte:
          "Accès, rectification, effacement, limitation, opposition et portabilité : écrire à " +
          'dpo@pointeo.example. Vous pouvez aussi saisir la CNIL.',
      },
      {
        titre: 'Cookies',
        texte:
          "Cookies de mesure d'audience uniquement avec votre accord, modifiable depuis « Gestion des cookies » " +
          "en bas de la page d'accueil ; votre choix est conservé 6 mois.",
      },
    ],
  },
};
