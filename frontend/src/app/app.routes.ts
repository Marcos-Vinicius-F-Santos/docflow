import { Routes } from '@angular/router';
import { documentRoutes } from './document/document.routes';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'documents/new' },
  ...documentRoutes,
  { path: '**', redirectTo: 'documents/new' },
];
