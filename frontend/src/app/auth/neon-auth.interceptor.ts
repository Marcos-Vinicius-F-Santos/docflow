import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, throwError } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { NeonAuthService } from './neon-auth.service';

export const neonAuthInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(NeonAuthService);

  return from(auth.getAccessToken()).pipe(
    switchMap((token) => {
      if (!token) {
        return throwError(() => new HttpErrorResponse({
          error: { code: 'AUTHENTICATION_REQUIRED' },
          status: 401,
          statusText: 'Unauthorized',
          url: request.url,
        }));
      }

      return next(request.clone({
        setHeaders: { Authorization: `Bearer ${token}` },
      }));
    }),
  );
};
