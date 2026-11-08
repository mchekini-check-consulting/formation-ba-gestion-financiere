import { Routes } from '@angular/router';
import { Clients } from './clients/clients';
import { CodeVerification } from './code-verification/code-verification';
import { CreerCompte } from './creer-compte/creer-compte';
import { Connexion } from './connexion/connexion';
import { Equipe } from './equipe/equipe';
import { Espace } from './espace/espace';
import { Dossier, Dossiers, EspaceClient } from './espace/pages';
import { ItemsPage } from './items-page/items-page';
import { Landing } from './landing/landing';
import { PageAVenir } from './landing/page-a-venir';
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
      { path: 'dossiers', component: Dossiers, data: { role: 'COLLABORATEUR' } },
      { path: 'dossiers/:id', component: Dossier, data: { role: 'COLLABORATEUR' } },
      { path: 'espace-client', component: EspaceClient, data: { role: 'DIRIGEANT' } },
      // US-19 : équipe du cabinet, réservée à ses administrateurs (RG1).
      { path: 'equipe', component: Equipe, data: { role: 'COLLABORATEUR', administrateur: true } },
    ],
  },
  // Lien de l'email d'invitation (US-19) : activation du compte avec l'US-08.
  { path: 'activer-compte', component: PageAVenir },
  { path: 'mentions-legales', component: PageAVenir },
  { path: 'cgu', component: PageAVenir },
  { path: 'confidentialite', component: PageAVenir },
];
