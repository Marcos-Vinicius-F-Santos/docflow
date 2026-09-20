import { Routes } from '@angular/router';

export const documentRoutes: Routes = [
  {
    path: 'documents/new',
    loadComponent: () =>
      import('./pages/document-upload-page').then(({ DocumentUploadPage }) => DocumentUploadPage),
  },
  {
    path: 'documents/:documentId',
    loadComponent: () =>
      import('./pages/document-detail-page').then(({ DocumentDetailPage }) => DocumentDetailPage),
  },
];
