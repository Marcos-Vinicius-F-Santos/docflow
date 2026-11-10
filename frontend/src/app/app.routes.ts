import { Routes } from '@angular/router';
import { documentRoutes } from './document/document.routes';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./hub/hub-page').then(({ HubPage }) => HubPage),
  },
  {
    path: 'auth',
    loadComponent: () => import('./auth/auth-page').then(({ AuthPage }) => AuthPage),
  },
  ...documentRoutes,
  { path: '**', redirectTo: '' },
];
