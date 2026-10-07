import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';
import { ToastService } from './toast.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('skillbridge_token');
  const router = inject(Router);
  const toastService = inject(ToastService);

  const authReq = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 || error.status === 403) {
        localStorage.removeItem('skillbridge_token');
        
        toastService.show('Tu sesión ha expirado y debes volver a iniciar sesión', [
          { label: 'Iniciar sesión', primary: true, action: () => router.navigate(['/login']) },
          { label: 'Ir al inicio', action: () => router.navigate(['/']) }
        ]);
      }
      return throwError(() => error);
    })
  );
};
