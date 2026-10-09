import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { AuthService } from '../core/auth.service';

export const adminGuard: CanActivateFn = (_route, state): boolean | UrlTree => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isLoggedIn() && auth.role() === 'ADMIN') {
    return true;
  }

  if (!auth.isLoggedIn()) {
    return router.createUrlTree(['/login'], {
      queryParams: {
        authRequired: true,
        returnUrl: state.url
      }
    });
  }

  return router.createUrlTree(['/']);
};

