import { Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { ToastService } from "./core/toast.service";
import { HistoryService } from './core/history.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  template: `
    <header class="nav">
      <div class="container nav-inner">
        <a routerLink="/" class="brand">SkillBridge AI</a>
        <nav>
          @if (auth.role() !== 'ADMIN') {
            <a routerLink="/">Servicios</a>
            <a routerLink="/book">Reservar</a>
            <a routerLink="/ai">IA</a>
          }
          @if (auth.role() === 'PROVIDER') {
            <a routerLink="/provider">Panel de proveedor</a>
          }
          @if (auth.role() === 'ADMIN') {
            <a routerLink="/admin">Panel Admin</a>
          }
          @if (!auth.isAuthenticated()) {
            <a routerLink="/login">Ingresar</a>
          } @else {
            @if (auth.role() !== 'ADMIN') {
              <a routerLink="/bookings">Mis reservas</a>
            }
            <a routerLink="/notifications">Notificaciones</a>
            <button class="link-button" (click)="auth.logout()">Salir</button>
          }
        </nav>
      </div>
    </header>
    <main><router-outlet /></main>

    @if (toastService.toast(); as t) {
      <div class="toast-overlay">
        <div class="toast-card">
          <p>{{ t.message }}</p>
          <div class="toast-actions">
            @for (btn of t.actions; track btn.label) {
              <button class="toast-btn" [class.primary]="btn.primary" (click)="executeToast(btn)">{{ btn.label }}</button>
            }
          </div>
        </div>
      </div>
    }

    @if (auth.sessionExpired()) {
      <div class="modal-overlay-blocking">
        <div class="modal-card-blocking">
          <h2>Sesión expirada</h2>
          <p>Tu sesión ha expirado y debes volver a iniciar sesión para continuar.</p>
          <button class="toast-btn primary" (click)="auth.forceLogout()">Ir al login</button>
        </div>
      </div>
    }
  `,
  styles: [`
    .nav{background:#fff;border-bottom:1px solid #e7ebf0;position:sticky;top:0;z-index:5}
    .nav-inner{height:64px;display:flex;align-items:center;justify-content:space-between}
    .brand{font-weight:800}.nav nav{display:flex;gap:18px;align-items:center}.link-button{border:0;background:none;cursor:pointer}
    
    .toast-overlay {
      position: fixed;
      bottom: 24px;
      left: 0;
      right: 0;
      display: flex;
      justify-content: center;
      z-index: 100;
    }
    .toast-card {
      background: #1e293b;
      color: #fff;
      padding: 16px 24px;
      border-radius: 12px;
      box-shadow: 0 10px 25px -5px rgba(0,0,0,0.3);
      display: flex;
      align-items: center;
      gap: 20px;
      animation: slideUp 0.3s ease-out;
    }
    .toast-card p { margin: 0; font-size: 0.95rem; font-weight: 500; }
    .toast-actions { display: flex; gap: 12px; }
    .toast-btn {
      background: transparent;
      border: 1px solid #cbd5e1;
      color: #fff;
      padding: 8px 16px;
      border-radius: 6px;
      cursor: pointer;
      font-weight: 600;
      font-size: 0.85rem;
      transition: all 0.2s;
    }
    .toast-btn:hover { background: rgba(255,255,255,0.1); }
    .toast-btn.primary {
      background: #3b82f6;
      border-color: #3b82f6;
    }
    .toast-btn.primary:hover {
      background: #2563eb;
    }
    @keyframes slideUp {
      from { transform: translateY(100px); opacity: 0; }
      to { transform: translateY(0); opacity: 1; }
    }
    .modal-overlay-blocking {
      position: fixed;
      top: 0; left: 0; right: 0; bottom: 0;
      background: rgba(0,0,0,0.7);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 9999;
    }
    .modal-card-blocking {
      background: #fff;
      color: #333;
      padding: 32px;
      border-radius: 12px;
      text-align: center;
      max-width: 400px;
      box-shadow: 0 10px 25px rgba(0,0,0,0.5);
    }
    .modal-card-blocking h2 { margin-top: 0; margin-bottom: 16px; font-size: 1.5rem; color: #1e293b; }
    .modal-card-blocking p { margin-bottom: 24px; color: #475569; }
    .modal-card-blocking .toast-btn { color: #333; border-color: #cbd5e1; }
    .modal-card-blocking .toast-btn.primary { color: #fff; }
  `]
})
export class AppComponent {
  auth = inject(AuthService);
  toastService = inject(ToastService);
  private history = inject(HistoryService);

  executeToast(btn: any) {
    btn.action();
    this.toastService.clear();
  }
}
