import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../services/auth';

export const authGuard: CanActivateFn = (_, state) => {
  const auth = inject(AuthService),
    router = inject(Router);
  return auth
    .restoreSession()
    .pipe(
      map((status) =>
        status === 'authenticated' && auth.isAuthenticated()
          ? true
          : status === 'error' || status === 'logout-error'
            ? router.createUrlTree(['/session-error'], { queryParams: { returnUrl: state.url } })
            : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } }),
      ),
    );
};
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService),
    router = inject(Router);
  return auth
    .restoreSession()
    .pipe(
      map((status) =>
        status === 'authenticated' && auth.isAuthenticated()
          ? router.createUrlTree(['/dashboard'])
          : status === 'error' || status === 'logout-error'
            ? router.createUrlTree(['/session-error'])
            : true,
      ),
    );
};
