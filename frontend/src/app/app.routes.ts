import { Routes } from '@angular/router';
import { documentRoutes } from './document/document.routes';
import { authGuard } from './auth/auth.guard';

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
  {
    path: 'logs',
    canActivate: [authGuard],
    loadComponent: () => import('./logs/logs-page').then(({ LogsPage }) => LogsPage),
  },
  ...documentRoutes,
  { path: '**', redirectTo: '' },
];
