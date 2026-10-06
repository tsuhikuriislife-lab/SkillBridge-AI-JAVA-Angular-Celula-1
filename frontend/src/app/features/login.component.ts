import { Component } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="container section"><div class="card auth-card">
      <h1>{{ mode === 'login' ? 'Ingresar' : 'Crear cuenta' }}</h1>
      @if (mode === 'register') { <label class="field">Nombre<input [(ngModel)]="name" name="name"></label> }
      <label class="field">Correo<input type="email" [(ngModel)]="email" name="email"></label>
      <label class="field">Contraseña<input type="password" [(ngModel)]="password" name="password"></label>
      @if (error) { <p class="error">{{ error }}</p> }
      <button class="btn" (click)="submit()">Continuar</button>
      <button class="btn secondary" (click)="toggle()">{{ mode === 'login' ? 'Crear cuenta' : 'Ya tengo cuenta' }}</button>
    </div></section>
  `,
  styles: [`.section{padding:50px 0}.auth-card{max-width:480px;margin:auto;display:grid;gap:10px}`]
})
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
