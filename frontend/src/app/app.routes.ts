import { Routes } from '@angular/router';
import { Clients } from './clients/clients';
import { Connexion } from './connexion/connexion';
import { ItemsPage } from './items-page/items-page';
import { Landing } from './landing/landing';
import { PageAVenir } from './landing/page-a-venir';

export const routes: Routes = [
  { path: '', component: Landing },
  { path: 'clients', component: Clients },
  { path: 'items', component: ItemsPage },
  { path: 'connexion', component: Connexion },
  // Écrans des US suivantes : code SMS (US-04), mot de passe oublié.
  { path: 'verification-code', component: PageAVenir },
  { path: 'mot-de-passe-oublie', component: PageAVenir },
  { path: 'creer-compte', component: PageAVenir },
  { path: 'mentions-legales', component: PageAVenir },
  { path: 'cgu', component: PageAVenir },
  { path: 'confidentialite', component: PageAVenir },
];
