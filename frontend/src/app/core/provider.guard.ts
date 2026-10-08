import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { Observable, map } from 'rxjs';
import { AuthService } from './auth.service';

export const providerGuard: CanActivateFn = (_route, state): boolean | UrlTree | Observable<boolean | UrlTree> => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isLoggedIn()) {
    return router.createUrlTree(['/login'], {
      queryParams: { authRequired: true, returnUrl: state.url }
    });
  }

  return authService.loadProfile().pipe(
    map(user => user?.role === 'PROVIDER' ? true : router.createUrlTree(['/']))
  );
};