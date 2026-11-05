import { Component, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TIERS, euros, prixMensuel, trancheDe } from '../tarifs';

interface Tile {
  title: string;
  text: string;
  soon?: boolean;
}

interface Faq {
  q: string;
  a: string;
}

type Profile = 'cabinets' | 'entreprises';
type CookieChoice = 'accepted' | 'refused';

const COOKIE_STORAGE_KEY = 'pointeo.cookies';
const COOKIE_CHOICE_MONTHS = 6;
const DEFAULT_DOSSIERS = 300;

@Component({
  imports: [RouterLink],
  selector: 'app-landing',
  styleUrl: './landing.scss',
  templateUrl: './landing.html',
})
export class Landing {
  protected readonly tiers = TIERS;
  protected readonly defaultDossiers = DEFAULT_DOSSIERS;

  protected readonly tiles: Record<Profile, Tile[]> = {
    cabinets: [
      {
        title: 'Synchronisation bancaire DSP2',
        text: 'Les comptes de vos clients lus par API chaque nuit, et en quelques minutes avec « Actualiser ».',
        soon: true,
      },
      {
        title: 'Catégorisation et rapprochement',
        text: 'Chaque opération classée selon vos règles, reliée à sa facture, puis lettrée.',
        soon: true,
      },
      {
        title: 'Écritures et export FEC',
        text: 'Les écritures passées sur le plan comptable du dossier, contrôlées, puis exportées vers votre logiciel.',
        soon: true,
      },
      {
        title: 'Préparation de la TVA',
        text: 'TVA collectée et déductible calculée par taux, prête à déclarer.',
        soon: true,
      },
      {
        title: 'Rapports financiers',
        text: 'Compte de résultat, évolution sur 12 mois et répartition des charges, exportables en PDF.',
        soon: true,
      },
      {
        title: 'Dossiers clients et équipe',
        text: 'Un dossier par entreprise, créé à partir du SIREN, et chaque collaborateur avec son rôle.',
      },
    ],
    entreprises: [
      {
        title: 'Déposer factures et notes de frais',
        text: 'Glissez vos PDF ou photos ; vos tickets de notes de frais seront lus automatiquement.',
      },
      {
        title: 'Connecter sa banque',
        text: 'Autorisez la lecture de vos comptes, en lecture seule, sans jamais donner vos identifiants.',
        soon: true,
      },
      {
        title: 'Trésorerie en temps réel',
        text: 'Votre solde à jour et ce qui doit entrer et sortir dans les 30 prochains jours.',
        soon: true,
      },
      {
        title: 'Échanges sécurisés avec le cabinet',
        text: 'Un seul espace pour tout transmettre, même si vous dirigez plusieurs sociétés.',
      },
    ],
  };

  protected readonly upcoming: Tile[] = [
    { title: 'Synchronisation bancaire', text: 'Les opérations de vos clients chaque matin, en lecture seule.' },
    { title: 'Rapprochement automatique', text: 'Pointéo propose la facture de chaque opération, vous validez.' },
    { title: 'CRM', text: 'Vos échanges et missions avec chaque client.' },
    { title: 'Suivi des temps', text: 'Le temps passé par dossier et par collaborateur.' },
  ];

  protected readonly included = [
    'Collaborateurs illimités, avec leurs rôles',
    'Espace client gratuit pour chaque entreprise suivie',
    "Dépôt des factures, hébergement dans l'Union européenne",
    'Mise en place : 1 500 € HT une seule fois, sans engagement',
  ];

  protected readonly faqs: Faq[] = [
    {
      q: 'Chaque collaborateur a-t-il son propre accès ?',
      a: 'Oui. L’administrateur invite chaque collaborateur avec un rôle : administrateur, associé ou comptable. Un comptable ne voit que les dossiers qui lui sont attribués.',
    },
    {
      q: 'Comment mon client accède-t-il à son espace ?',
      a: 'Depuis son dossier, vous l’invitez par email. Il choisit son mot de passe et accède à son espace de dépôt.',
    },
    {
      q: 'Quels fichiers mes clients peuvent-ils déposer ?',
      a: 'Des PDF, JPG et PNG, jusqu’à 10 Mo par fichier et 20 fichiers par dépôt.',
    },
    {
      q: 'Où sont stockées les factures ?',
      a: 'Dans l’Union européenne, chiffrées, et conservées 10 ans, la durée légale des pièces comptables.',
    },
    {
      q: 'Comment sont protégées mes données ?',
      a: 'Chaque cabinet ne voit que ses dossiers, chaque client que son entreprise, et chaque connexion est confirmée par un code reçu par SMS.',
    },
    {
      q: 'Quand arrivent les modules CRM et Suivi des temps ?',
      a: 'Ils sont visibles dans chaque dossier avec la mention « Bientôt disponible » et seront activés dans une prochaine version.',
    },
    {
      q: 'Pointéo remplace-t-il notre logiciel comptable ?',
      a: 'Non. Pointéo le complète : il prépare les écritures et les exporte au format FEC, que vous importez dans votre logiciel.',
    },
  ];

  protected readonly profile = signal<Profile>('cabinets');
  protected readonly openFaq = signal(0);
  protected readonly dossiers = signal(DEFAULT_DOSSIERS);
  protected readonly showCookies = signal(!this.hasValidCookieChoice());

  /** La tranche atteinte fixe le prix de tous les dossiers du cabinet. */
  protected readonly tier = computed(() => trancheDe(this.dossiers()));

  protected readonly total = computed(() => prixMensuel(this.dossiers()));

  protected euros(amount: number): string {
    return euros(amount);
  }

  protected toggleFaq(i: number): void {
    this.openFaq.update((open) => (open === i ? -1 : i));
  }

  protected setDossiers(event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    this.dossiers.set(Math.max(0, Math.floor(value) || 0));
  }

  protected chooseCookies(choice: CookieChoice): void {
    const expiresAt = new Date();
    expiresAt.setMonth(expiresAt.getMonth() + COOKIE_CHOICE_MONTHS);
    localStorage.setItem(COOKIE_STORAGE_KEY, JSON.stringify({ choice, expiresAt }));
    this.showCookies.set(false);
  }

  private hasValidCookieChoice(): boolean {
    try {
      const stored = JSON.parse(localStorage.getItem(COOKIE_STORAGE_KEY) ?? 'null');
      return stored !== null && new Date(stored.expiresAt) > new Date();
    } catch {
      return false;
    }
  }
}
