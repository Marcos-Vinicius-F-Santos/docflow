import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { from, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { NeonAuthService } from './neon-auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(NeonAuthService);
  const router = inject(Router);

  if (!auth.isConfigured()) {
    return router.createUrlTree(['/auth']);
  }

  return from(auth.getSession()).pipe(
    map((result) => result.data ? true : router.createUrlTree(['/auth'])),
    catchError(() => of(router.createUrlTree(['/auth']))),
  );
};
