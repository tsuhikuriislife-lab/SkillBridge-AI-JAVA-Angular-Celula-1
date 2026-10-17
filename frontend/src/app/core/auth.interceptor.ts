import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';
import { AuthService } from './auth.service';

function getXsrfCookie(): string | null {
  if (typeof document === 'undefined') return null;
  const match = document.cookie.match(/(^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[2]) : null;
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);

  // HU-14: No se usa localStorage ni encabezado Authorization Bearer.
  // La sesión se autentica mediante cookies HttpOnly del navegador (withCredentials).
  let modifiedReq = req.clone({
    withCredentials: true
  });

  // HU-14: Las solicitudes que modifican datos requieren token CSRF en el encabezado X-XSRF-TOKEN
  const mutatingMethods = ['POST', 'PUT', 'PATCH', 'DELETE'];
  if (mutatingMethods.includes(req.method.toUpperCase()) && !req.headers.has('X-XSRF-TOKEN')) {
    const xsrfToken = getXsrfCookie();
    if (xsrfToken) {
      modifiedReq = modifiedReq.clone({
        setHeaders: {
          'X-XSRF-TOKEN': xsrfToken
        }
      });
    }
  }

  return next(modifiedReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !req.url.includes('/auth/login') && !req.url.includes('/auth/register') && !req.url.includes('/users/me')) {
        if (authService.isAuthenticated()) {
          authService.triggerSessionExpired();
        }
      }
      return throwError(() => error);
    })
  );
};
