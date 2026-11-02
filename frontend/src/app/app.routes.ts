import { Routes } from '@angular/router';
import { CabinetsPage } from './cabinets-page/cabinets-page';
import { Clients } from './clients/clients';
import { ItemsPage } from './items-page/items-page';
import { Landing } from './landing/landing';
import { PageAVenir } from './landing/page-a-venir';

export const routes: Routes = [
  { path: '', component: Landing },
  { path: 'clients', component: Clients },
  { path: 'items', component: ItemsPage },
  { path: 'connexion', component: PageAVenir },
  // Bouton « Créer un compte » de la landing : formulaire des cabinets (backend cabinets)
  { path: 'creer-compte', component: CabinetsPage, title: 'Pointéo – Créer un compte' },
  { path: 'mentions-legales', component: PageAVenir },
  { path: 'cgu', component: PageAVenir },
  { path: 'confidentialite', component: PageAVenir },
];
