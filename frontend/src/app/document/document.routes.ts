import { Routes } from '@angular/router';

export const documentRoutes: Routes = [
  {
    path: 'documents/:documentId',
    loadComponent: () =>
      import('./pages/document-detail-page').then(({ DocumentDetailPage }) => DocumentDetailPage),
  },
];
