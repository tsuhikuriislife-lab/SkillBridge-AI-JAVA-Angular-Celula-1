import { Component } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../core/auth.service';



export class LoginComponent {
  mode: 'login' | 'register' = 'login'; name = ''; email = ''; password = ''; error = '';
  constructor(
    private auth: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {
    this.route.queryParamMap
      .pipe(takeUntilDestroyed())
      .subscribe(params => {
        const authRequiredMessage = 'Primero debes iniciar sesión para ver esta vista.';
        if (params.get('authRequired') === 'true') {
          this.error = authRequiredMessage;
        } else if (this.error === authRequiredMessage) {
          this.error = '';
        }
      });
  }
  toggle() { this.mode = this.mode === 'login' ? 'register' : 'login'; this.error = ''; }
  submit() {
    this.error = '';
    const request = this.mode === 'login' ? this.auth.login(this.email, this.password) : this.auth.register(this.name, this.email, this.password);
    request.subscribe({ next: () => this.router.navigateByUrl('/'), error: e => this.error = e?.error?.detail || 'No fue posible autenticar.' });
  }
}
