import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-provider-dashboard',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="dashboard container">
      <aside class="sidebar">
        <nav>
          <a routerLink="new" routerLinkActive="active">Agregar nuevo servicio</a>
          <a routerLink="services" routerLinkActive="active">Mis servicios</a>
        </nav>
      </aside>
      <section class="content">
        <router-outlet></router-outlet>
      </section>
    </div>
  `,
  styles: [`
    .dashboard{display:flex;gap:24px;padding:32px 0;min-height:80vh}
    .sidebar{width:250px;flex-shrink:0}
    .sidebar nav{display:flex;flex-direction:column;gap:8px}
    .sidebar a{padding:12px 16px;text-decoration:none;color:#5f6368;border-radius:8px;font-weight:500}
    .sidebar a:hover{background:#f3f6fb;color:#1a73e8}
    .sidebar a.active{background:#e8f0fe;color:#1a73e8}
    .content{flex:1}
  `]
})
export class ProviderDashboardComponent {}
