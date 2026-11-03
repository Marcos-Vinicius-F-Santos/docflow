import { Routes } from '@angular/router';
import { documentRoutes } from './document/document.routes';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./hub/hub-page').then(({ HubPage }) => HubPage),
  },
  {
    path: 'logs',
    loadComponent: () => import('./logs/logs-page').then(({ LogsPage }) => LogsPage),
  },
  ...documentRoutes,
  { path: '**', redirectTo: '' },
];
