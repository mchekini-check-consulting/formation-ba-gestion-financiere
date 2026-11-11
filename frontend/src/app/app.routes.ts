import { Routes } from '@angular/router';
import { ActiverCompte } from './activation/activer-compte';
import { Clients } from './clients/clients';
import { ApercuFacture } from './dossiers/apercu-facture';
import { CodeVerification } from './code-verification/code-verification';
import { CreerCompte } from './creer-compte/creer-compte';
import { CreerDossier } from './dossiers/creer-dossier';
import { Connexion } from './connexion/connexion';
import { Equipe } from './equipe/equipe';
import { Espace } from './espace/espace';
import { ListeDossiers } from './dossiers/liste-dossiers';
import { Dossier } from './dossiers/dossier';
import { EspaceClient } from './espace/espace-client';
import { ItemsPage } from './items-page/items-page';
import { Landing } from './landing/landing';
import { PageLegale } from './legal/page-legale';
import { MotDePasseOublie } from './mot-de-passe/mot-de-passe-oublie';
import { NouveauMotDePasse } from './mot-de-passe/nouveau-mot-de-passe';
import { sessionGuard } from './session/session.guard';

export const routes: Routes = [
  { path: '', component: Landing, pathMatch: 'full' },
  { path: 'clients', component: Clients },
  { path: 'items', component: ItemsPage },
  // Authentification (EP-02) : US-03 connexion, US-04 code reçu par email, US-06 mot de passe oublié.
  { path: 'connexion', component: Connexion },
  { path: 'verification-code', component: CodeVerification },
  { path: 'mot-de-passe-oublie', component: MotDePasseOublie },
  { path: 'reinitialiser-mot-de-passe', component: NouveauMotDePasse },
  // US-36 : création du compte d'un cabinet (informations, code email, cabinet prêt).
  { path: 'creer-compte', component: CreerCompte },
  // Pages connectées (US-07) : session vérifiée par le backend à chaque page ouverte.
  {
    path: '',
    component: Espace,
    canActivateChild: [sessionGuard],
    children: [
      // US-09 : liste des dossiers du cabinet.
      { path: 'dossiers', component: ListeDossiers, data: { role: 'COLLABORATEUR' } },
      // US-10 : créer un dossier client, qui s'ouvre ensuite (RG3).
      { path: 'dossiers/nouveau', component: CreerDossier, data: { role: 'COLLABORATEUR' } },
      // US-11 : ouvrir un dossier (en-tête, onglets) ; droits vérifiés par le backend.
      { path: 'dossiers/:id', component: Dossier, data: { role: 'COLLABORATEUR' } },
      // US-13 : aperçu et téléchargement d'une facture ; sans connexion, retour à la connexion (RG3).
      { path: 'dossiers/:id/factures/:factureId', component: ApercuFacture, data: { role: 'COLLABORATEUR' } },
      { path: 'espace-client', component: EspaceClient, data: { role: 'DIRIGEANT' } },
      // US-19 : équipe du cabinet, réservée à ses administrateurs (RG1).
      { path: 'equipe', component: Equipe, data: { role: 'COLLABORATEUR', administrateur: true } },
    ],
  },
  // Lien de l'email d'invitation (US-19) : activation du compte avec l'US-08.
  { path: 'activer-compte', component: ActiverCompte },
  // US-34 : pages légales du pied de page, publiques (sans connexion).
  { path: 'mentions-legales', component: PageLegale, data: { page: 'mentions-legales' } },
  { path: 'cgu', component: PageLegale, data: { page: 'cgu' } },
  { path: 'confidentialite', component: PageLegale, data: { page: 'confidentialite' } },
];
