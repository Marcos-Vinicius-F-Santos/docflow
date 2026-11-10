import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { NeonAuthService } from './neon-auth.service';

export const neonAuthInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(NeonAuthService);

  return from(auth.getAccessToken()).pipe(
    switchMap((token) => {
      if (!token) {
        return next(request);
      }

      return next(request.clone({
        setHeaders: { Authorization: `Bearer ${token}` },
      }));
    }),
  );
};
