import { Routes } from '@angular/router';
import { Clients } from './clients/clients';
import { CodeVerification } from './code-verification/code-verification';
import { Connexion } from './connexion/connexion';
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
  // Authentification (EP-02) : US-03 connexion, US-04 code SMS, US-06 mot de passe oublié.
  { path: 'connexion', component: Connexion },
  { path: 'verification-code', component: CodeVerification },
  { path: 'mot-de-passe-oublie', component: MotDePasseOublie },
  { path: 'reinitialiser-mot-de-passe', component: NouveauMotDePasse },
  // Pages connectées (US-07) : session vérifiée par le backend à chaque page ouverte.
  {
    path: '',
    component: Espace,
    canActivateChild: [sessionGuard],
    children: [
      { path: 'dossiers', component: Dossiers, data: { role: 'COLLABORATEUR' } },
      { path: 'dossiers/:id', component: Dossier, data: { role: 'COLLABORATEUR' } },
      { path: 'espace-client', component: EspaceClient, data: { role: 'DIRIGEANT' } },
    ],
  },
  { path: 'creer-compte', component: PageAVenir },
  { path: 'mentions-legales', component: PageAVenir },
  { path: 'cgu', component: PageAVenir },
  { path: 'confidentialite', component: PageAVenir },
];
