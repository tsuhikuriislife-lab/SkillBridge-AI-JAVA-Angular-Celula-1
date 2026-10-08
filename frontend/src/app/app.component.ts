import { Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  template: `
    <header class="nav">
      <div class="container nav-inner">
        <a routerLink="/" class="brand">SkillBridge AI</a>
        <nav>
          <a routerLink="/">Servicios</a>
          <a routerLink="/book">Reservar</a>
          <a routerLink="/ai">IA</a>
          @if (!auth.isAuthenticated()) {
            <a routerLink="/login">Ingresar</a>
          } @else {
            <a routerLink="/bookings">Mis reservas</a>
            <a routerLink="/notifications">Notificaciones</a>
            <button class="link-button" (click)="auth.logout()">Salir</button>
          }
        </nav>
      </div>
    </header>
    <main><router-outlet /></main>
  `,
  styles: [`
    .nav{background:#fff;border-bottom:1px solid #e7ebf0;position:sticky;top:0;z-index:5}
    .nav-inner{height:64px;display:flex;align-items:center;justify-content:space-between}
    .brand{font-weight:800}.nav nav{display:flex;gap:18px;align-items:center}.link-button{border:0;background:none;cursor:pointer}
  `]
})
export class AppComponent {
  auth = inject(AuthService);
}
