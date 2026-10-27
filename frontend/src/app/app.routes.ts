import { Routes } from '@angular/router';
import { documentRoutes } from './document/document.routes';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./hub/hub-page').then(({ HubPage }) => HubPage),
  },
  ...documentRoutes,
  { path: '**', redirectTo: 'documents/new' },
];
