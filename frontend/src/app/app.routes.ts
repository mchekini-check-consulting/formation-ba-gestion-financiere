import { Routes } from '@angular/router';
import { CabinetsPage } from './cabinets-page/cabinets-page';
import { ItemsPage } from './items-page/items-page';
import { Landing } from './landing/landing';

export const routes: Routes = [
  { path: '', component: Landing, title: 'Pointéo – Comptabilité connectée pour les cabinets' },
  { path: 'cabinets', component: CabinetsPage, title: 'Pointéo – Créer un compte' },
  { path: 'items', component: ItemsPage, title: 'Items' },
  { path: '**', redirectTo: '' },
];
