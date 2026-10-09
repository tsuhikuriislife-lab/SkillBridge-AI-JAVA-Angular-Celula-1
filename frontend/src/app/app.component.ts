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
        <a routerLink="/" class="brand">
          <img src="images/Multimedia/Logo/Logo-3.svg" alt="SteelBridge AI" class="brand__logo">
        </a>

        <!-- Menú móvil (checkbox hack, sin TS) -->
        <input type="checkbox" id="nav-toggle" class="nav__toggle">
        <label for="nav-toggle" class="nav__burger" aria-label="Abrir menú">
          <i class="bx bx-menu"></i>
        </label>

        <nav class="nav__menu">
          @if (auth.role() !== 'ADMIN') {
            <a routerLink="/" class="nav__link">Servicios</a>
            <a routerLink="/book" class="nav__link"> Reservar</a>
            <a routerLink="/ai" class="nav__link"><i class="bx bx-sparkles-alt"></i> IA</a>
          }

          @if (auth.role() === 'PROVIDER') {
            <a routerLink="/provider" class="nav__link nav__link--provider">
              <i class="bx bx-widget-vertical"></i> Panel de proveedor
            </a>
          }

          @if (auth.role() === 'ADMIN') {
            <a routerLink="/admin" class="nav__link"><i class="bx bx-shield-quarter"></i> Panel Admin</a>
          }

          @if (!auth.isAuthenticated()) {
            <a routerLink="/login" class="nav__link nav__cta">Ingresar</a>
          } @else {
            @if (auth.role() !== 'ADMIN') {
              <a routerLink="/bookings" class="nav__link">Mis reservas</a>
            }
            <a routerLink="/notifications" class="nav__link nav__link--icon" aria-label="Notificaciones">
              <i class="bx bx-bell"></i> Notificaciones
            </a>
            <a class="nav__link nav__cta" style="cursor: pointer; user-select: none;" type="button" (click)="auth.logout()">
              Salir <i class="bx bx-arrow-out-right-square-half"></i>
            </a>
          }
        </nav>

      </div>
    </header>

    <main><router-outlet /></main>

    <!-- ==================== FOOTER ==================== -->
    <footer class="footer">
      <span class="footer__shape footer__shape--1" aria-hidden="true"></span>
      <span class="footer__shape footer__shape--2" aria-hidden="true"></span>

      <div class="footer__grid">

        <!-- Marca -->
        <div class="footer__brand">
          <a routerLink="/" class="footer__logo">
            <img src="images/Multimedia/Logo/Logo-4.svg" alt="SteelBridge AI" class="footer__logo-img">
          </a>
          <p class="footer__description">
            La plataforma que te conecta con servicios de formación tecnológica
            ofrecidos por terceros, con recomendaciones de IA adaptadas a tus objetivos.
          </p>
          <!-- TODO: enlaces de redes sociales (reemplazar spans por <a> cuando existan rutas) -->
          <div class="footer__socials">
            <span class="footer__social footer__social--placeholder" title="Próximamente"><i class="bxl bx-facebook"></i></span>
            <span class="footer__social footer__social--placeholder" title="Próximamente"><i class="bxl bx-instagram"></i></span>
            <a href="https://github.com/tsuhikuriislife-lab/SkillBridge-AI-JAVA-Angular-Celula-1" target="_blank" rel="noopener noreferrer" class="footer__social" title="GitHub"><i class="bxl bx-github"></i></a>            <span class="footer__social footer__social--placeholder" title="Próximamente"><i class="bxl bx-youtube"></i></span>
          </div>
        </div>

        <!-- Plataforma (solo enlaces existentes) -->
        <div class="footer__column">
          <h4 class="footer__title">Plataforma</h4>
          <a routerLink="/" class="footer__link">Servicios</a>
          <a routerLink="/book" class="footer__link">Reservar</a>
          <a routerLink="/ai" class="footer__link">Asistente IA</a>
        </div>

        <!-- Cuenta (según autenticación y rol, enlaces existentes) -->
        <div class="footer__column">
          <h4 class="footer__title">Cuenta</h4>
          @if (!auth.isAuthenticated()) {
            <a routerLink="/login" class="footer__link">Ingresar</a>
            <!-- TODO: enlace de registro cuando exista la ruta pública -->
            <span class="footer__link footer__link--placeholder">Crear cuenta</span>
          } @else {
            <a routerLink="/bookings" class="footer__link">Mis reservas</a>
            <a routerLink="/notifications" class="footer__link">Notificaciones</a>
            @if (auth.role() === 'PROVIDER') {
              <a routerLink="/provider" class="footer__link">Panel de proveedor</a>
            }
            <button class="footer__link footer__link--button" type="button" (click)="auth.logout()">Cerrar sesión</button>
          }
        </div>

        <!-- Soporte (placeholders, sin rutas aún) -->
        <div class="footer__column">
          <h4 class="footer__title">Soporte</h4>
          <!-- TODO: reemplazar por routerLink cuando existan las rutas -->
          <span class="footer__link footer__link--placeholder">Contacto</span>
          <span class="footer__link footer__link--placeholder">Preguntas frecuentes</span>
          <span class="footer__link footer__link--placeholder">Términos y condiciones</span>
          <span class="footer__link footer__link--placeholder">Privacidad</span>
        </div>

      </div>

      <div class="footer__bottom">
        <span>© 2026 SteelBridge AI. Todos los derechos reservados.</span>
        <span class="footer__note"><i class="bx bx-shield-quarter"></i> Los servicios son ofrecidos por terceros verificados</span>
      </div>
    </footer>

    <!-- Toast (sin cambios en la lógica) -->
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
    /* ============================================================
       HEADER DE NAVEGACIÓN
       ============================================================ */
    .nav {
      background: rgb(var(--color-white));
      border-bottom: 1px solid rgb(var(--color-border));
      position: sticky;
      top: 0;
      z-index: 100;
      box-shadow: 0 2px 12px rgb(var(--color-background) / 6%);
    }

    .nav-inner {
      height: 64px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 20px;
    }

    .brand {
      display: inline-flex;
      align-items: center;

      text-decoration: none;
    }

    .brand__logo {
      trasform:
      display: block;

      height: 30px;
      width: auto;

      transform: translateY(5px);

      object-fit: contain;
    }

    /* ---------- Menú ---------- */
    .nav__menu {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .nav__link {
      display: inline-flex;
      align-items: center;
      gap: 6px;

      padding: 8px 14px;

      border-radius: 10px;

      font-family: "Plus Jakarta Sans", sans-serif;
      font-size: 14px;
      font-weight: 600;

      color: rgb(var(--color-text));
      text-decoration: none;

      transition: background-color 0.2s ease, color 0.2s ease;
    }

    .nav__link:hover {
      background-color: rgb(var(--color-primary) / 7%);
      color: rgb(var(--color-primary));
    }

    .nav__link:focus-visible {
      outline: 3px solid rgb(var(--color-primary) / 35%);
      outline-offset: 2px;
    }

    .nav__link--provider i {
      color: rgb(var(--status-in-progress));
    }

    .nav__link--icon i {
      font-size: 16px;
    }

    /* Botón "Ingresar" */
    .nav__cta {
      background-color: rgb(var(--color-primary));
      color: rgb(var(--color-white)) !important;

      padding: 9px 20px;

      box-shadow: 0 6px 16px rgb(var(--color-primary) / 30%);

      transition: background-color 0.2s ease, transform 0.2s ease;
    }

    .nav__cta:hover {
      background-color: rgb(35 84 190);
      transform: translateY(-1px);
    }

    /* Botón "Salir" (es <button>, no <a>) */
    .nav__link--logout {
      border: none;
      background: transparent;

      cursor: pointer;
    }

    /* ---------- Checkbox hack (menú móvil sin TS) ---------- */
    .nav__toggle {
      display: none;
    }

    .nav__burger {
      display: none;

      align-items: center;
      justify-content: center;

      width: 42px;
      height: 42px;

      border-radius: 10px;

      font-size: 24px;
      color: rgb(var(--color-dark));

      cursor: pointer;

      transition: background-color 0.2s ease;
    }

    .nav__burger:hover {
      background-color: rgb(var(--color-primary) / 7%);
    }


    /* ============================================================
       FOOTER
       ============================================================ */
    .footer {
      position: relative;

      margin-top: auto;
      padding: 70px 6% 0;

      background: linear-gradient(160deg, #093b8c 0%, #0959db 60%, #2f7cf6 100%);
      color: rgb(var(--color-white));

      font-family: "Plus Jakarta Sans", sans-serif;

      overflow: hidden;
    }

    /* Textura de puntos, coherente con la landing */
    .footer::before {
      content: "";
      position: absolute;
      inset: 0;

      background-image: radial-gradient(rgb(var(--color-white) / 10%) 1px, transparent 1px);
      background-size: 26px 26px;

      pointer-events: none;
    }

    .footer__shape {
      position: absolute;

      border-radius: 50%;

      background-color: rgb(var(--color-white) / 6%);

      pointer-events: none;
    }

    .footer__shape--1 {
      top: -80px;
      right: -60px;

      width: 280px;
      height: 280px;
    }

    .footer__shape--2 {
      bottom: 60px;
      left: -90px;

      width: 240px;
      height: 240px;
    }

    .footer__grid {
      position: relative;

      display: grid;

      max-width: 1180px;

      margin: 0 auto;

      grid-template-columns: 1.6fr 1fr 1fr 1fr;
      gap: 40px;
    }

    /* Marca */
    .footer__brand {
      display: flex;
      flex-direction: column;

      gap: 16px;
    }

    .footer__logo {
      display: inline-flex;
      align-items: center;

      text-decoration: none;
    }

    .footer__logo-img {
      display: block;

      height: 34px;
      width: auto;

      object-fit: contain;

      /* Si el logo SVG es oscuro, esta clase lo fuerza a blanco.
         Si tu logo ya es claro, elimina la siguiente línea: */
      filter: brightness(0) invert(1);
    }

    .footer__description {
      max-width: 320px;

      font-size: 13.5px;
      font-weight: 500;
      line-height: 1.65;

      color: rgb(var(--color-white) / 75%);
    }

    .footer__socials {
      display: flex;

      gap: 10px;
    }

    .footer__social {
      display: flex;

      width: 38px;
      height: 38px;

      align-items: center;
      justify-content: center;

      border-radius: 12px;

      background-color: rgb(var(--color-white) / 12%);
      border: 1px solid rgb(var(--color-white) / 20%);

      font-size: 18px;
      color: rgb(var(--color-white));

      transition: background-color 0.2s ease, transform 0.2s ease;
    }

    .footer__social--placeholder {
      opacity: 0.45;
      cursor: not-allowed;
    }

    .footer__social--placeholder:hover {
      background-color: rgb(var(--color-white) / 12%);
      transform: none;
    }

    /* Columnas */
    .footer__column {
      display: flex;
      flex-direction: column;

      gap: 12px;
    }

    .footer__title {
      margin-bottom: 6px;

      font-size: 12px;
      font-weight: 800;
      letter-spacing: 1.8px;
      text-transform: uppercase;

      color: rgb(var(--color-white) / 60%);
    }

    .footer__link {
      display: inline-flex;
      align-items: center;
      gap: 8px;

      width: fit-content;

      font-size: 14px;
      font-weight: 500;

      color: rgb(var(--color-white) / 85%);
      text-decoration: none;

      transition: color 0.2s ease, transform 0.2s ease;
    }

    a.footer__link:hover {
      color: rgb(var(--color-white));
      transform: translateX(4px);
    }

    .footer__link--placeholder {
      opacity: 0.45;
      cursor: not-allowed;
    }

    .footer__link--placeholder:hover {
      transform: none;
    }

    /* "Cerrar sesión" es <button> dentro del footer */
    .footer__link--button {
      border: none;
      background: transparent;
      padding: 0;

      text-align: left;

      cursor: pointer;
    }

    .footer__link--button:hover {
      color: rgb(var(--color-white));
      transform: translateX(4px);
    }

    /* Barra inferior */
    .footer__bottom {
      position: relative;

      display: flex;

      max-width: 1180px;

      margin: 56px auto 0;
      padding: 22px 0;

      gap: 14px;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;

      border-top: 1px solid rgb(var(--color-white) / 15%);

      font-size: 12.5px;
      font-weight: 500;

      color: rgb(var(--color-white) / 60%);
    }

    .footer__note {
      display: inline-flex;
      align-items: center;
      gap: 7px;
    }

    .footer__note i {
      font-size: 15px;
      color: rgb(var(--color-white) / 80%);
    }


    /* ============================================================
       TOAST (estilos heredados del componente)
       ============================================================ */
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
      to   { transform: translateY(0); opacity: 1; }
    }


    /* ============================================================
       RESPONSIVE
       ============================================================ */
    @media (max-width: 900px) {
      .footer__grid {
        grid-template-columns: 1fr 1fr;
        gap: 36px;
      }

      .footer__brand {
        grid-column: 1 / -1;
      }
    }

    @media (max-width: 768px) {
      /* Menú hamburguesa */
      .nav__burger {
        display: flex;
      }

      .nav__menu {
        position: absolute;
        top: 64px;
        left: 0;
        right: 0;

        flex-direction: column;
        align-items: stretch;

        padding: 12px 6% 18px;
        gap: 4px;

        background-color: rgb(var(--color-white));
        border-bottom: 1px solid rgb(var(--color-border));
        box-shadow: 0 16px 30px rgb(var(--color-background) / 12%);

        opacity: 0;
        visibility: hidden;
        transform: translateY(-12px);

        transition: opacity 0.25s ease, transform 0.25s ease, visibility 0.25s;
      }

      .nav__toggle:checked ~ .nav__menu {
        opacity: 1;
        visibility: visible;
        transform: translateY(0);
      }

      .nav__toggle:checked ~ .nav__burger {
        background-color: rgb(var(--color-primary) / 10%);
        color: rgb(var(--color-primary));
      }

      .nav__link {
        padding: 12px 14px;
      }

      .nav__cta {
        text-align: center;
        justify-content: center;

        margin-top: 6px;
      }
    }

    @media (max-width: 520px) {
      .footer__grid {
        grid-template-columns: 1fr;
        gap: 30px;
      }

      .footer__bottom {
        flex-direction: column;
        align-items: flex-start;
        gap: 8px;
      }

      /* El toast ocupa casi todo el ancho en móvil */
      .toast-card {
        margin: 0 16px;
        flex-wrap: wrap;
        gap: 12px;
      }
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