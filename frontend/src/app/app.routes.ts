import { Routes } from '@angular/router';
import { Clients } from './clients/clients';
import { ItemsPage } from './items-page/items-page';
import { Landing } from './landing/landing';
import { PageAVenir } from './landing/page-a-venir';

export const routes: Routes = [
  { path: '', component: Landing },
  { path: 'clients', component: Clients },
  { path: 'items', component: ItemsPage },
  { path: 'connexion', component: PageAVenir },
  { path: 'creer-compte', component: PageAVenir },
  { path: 'mentions-legales', component: PageAVenir },
  { path: 'cgu', component: PageAVenir },
  { path: 'confidentialite', component: PageAVenir },
];
