import { Routes } from '@angular/router';
import { authGuard } from '../auth/auth.guard';

export const documentRoutes: Routes = [
  {
    path: 'documents/:documentId',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/document-detail-page').then(({ DocumentDetailPage }) => DocumentDetailPage),
  },
];
