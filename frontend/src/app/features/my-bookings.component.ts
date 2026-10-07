import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { BookingService, BookingSummary, BookingSort, BookingActivityFilter, BookingPage } from '../core/booking.service';

@Component({
  selector: 'app-my-bookings',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, CurrencyPipe],
  template: `
    <div class="booking-container">
      <div class="header-section">
        <div>
          <h2>Mis Reservas</h2>
          <p class="subtitle">Gestiona y consulta tus sesiones programadas</p>
        </div>
        <a routerLink="/book" class="btn-primary">Nueva Reserva</a>
      </div>

      <section class="booking-filters" aria-label="Filtros de reservas">
        <label class="filter-control">
          Ordenar por
          <select [ngModel]="sort()" (ngModelChange)="changeSort($event)" [disabled]="loading()">
            <option value="TITLE_ASC">Nombre A–Z</option>
            <option value="TITLE_DESC">Nombre Z–A</option>
            <option value="DATE_DESC">Fecha más reciente</option>
            <option value="DATE_ASC">Fecha más antigua</option>
            <option value="PRICE_ASC">Precio menor a mayor</option>
            <option value="PRICE_DESC">Precio mayor a menor</option>
          </select>
        </label>
        <label class="filter-control">
          Estado
          <select [ngModel]="activity()" (ngModelChange)="changeActivity($event)" [disabled]="loading()">
            <option value="ALL">Todas</option>
            <option value="ACTIVE">Activas</option>
            <option value="INACTIVE">No activas</option>
          </select>
        </label>
        <label class="page-size" style="margin-left: auto;">
          Mostrar
          <select [ngModel]="pageSize()" (ngModelChange)="changePageSize($event)" [disabled]="loading()">
            <option [ngValue]="10">10</option>
            <option [ngValue]="25">25</option>
            <option [ngValue]="50">50</option>
          </select>
          por página
        </label>
      </section>

      <!-- ESTADO DE CARGA -->
      @if (loading()) {
        <div class="state-container">
          <div class="spinner"></div>
          <p>Cargando tus reservas...</p>
        </div>
      }

      <!-- ESTADO DE ERROR -->
      @else if (error()) {
        <div class="state-container error-state">
          <svg class="state-icon error-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="8" x2="12" y2="12"/>
            <line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
          <h3>Ocurrió un error</h3>
          <p>{{ error() }}</p>
          <button class="btn-retry" (click)="loadPage()">Reintentar</button>
        </div>
      }

      <!-- ESTADO VACÍO -->
      @else if (reservas().length === 0) {
        <div class="state-container empty-state">
          <div class="empty-icon-wrapper">
            <svg class="state-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
              <line x1="16" y1="2" x2="16" y2="6"/>
              <line x1="8" y1="2" x2="8" y2="6"/>
              <line x1="3" y1="10" x2="21" y2="10"/>
            </svg>
          </div>
          @if (totalElements() === 0 && activity() === 'ALL') {
            <h3>Aún no tienes reservas</h3>
            <p>Explora nuestro catálogo de mentorías y servicios para programar tu primera sesión.</p>
            <a routerLink="/book" class="btn-primary">Explorar Servicios</a>
          } @else {
            <h3>No hay reservas que coincidan</h3>
            <p>Prueba con otro estado o criterio de orden.</p>
          }
        </div>
      }

      <!-- ESTADO CON DATOS -->
      @else {
        <div class="table-responsive">
          <table class="booking-table">
            <thead>
              <tr>
                <th>SERVICIO</th>
                <th>PRECIO</th>
                <th>ESTADO</th>
                <th>FECHA / SESIÓN</th>
                <th>PROGRESO</th>
                <th class="text-center">ACCIÓN</th>
              </tr>
            </thead>
            <tbody>
              @for (reserva of reservas(); track reserva.id) {
                <tr class="table-row">
                  <!-- Servicio -->
                  <td>
                    <div class="service-cell">
                      <div class="service-icon">
                        <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                          <path d="M22 12.5V17a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-4.5"/>
                          <path d="M2 12a10 10 0 0 1 10-10 10 10 0 0 1 10 10"/>
                          <path d="M12 2v10"/>
                        </svg>
                      </div>
                      <div class="service-info">
                        <span class="service-title">{{ reserva.offeringTitle || ('Reserva #' + reserva.id.substring(0, 8)) }}</span>
                        <span class="service-subtitle">ID: {{ reserva.id.substring(0, 8) }}</span>
                      </div>
                    </div>
                  </td>

                  <!-- Precio -->
                  <td>
                    <span class="category-badge" style="background-color: #f1f5f9; color: #475569;">
                      {{ reserva.price | currency:'COP':'symbol-narrow':'1.0-0':'es-CO' }}
                    </span>
                  </td>

                  <!-- Estado -->
                  <td>
                    <div class="status-container" [ngClass]="getStatusClass(reserva.status)">
                      <span class="status-text">{{ getStatusLabel(reserva.status) }}</span>
                    </div>
                  </td>

                  <!-- Fecha -->
                  <td>
                    <span class="session-date">{{ formatearFecha(reserva.scheduledAt) }}</span>
                  </td>

                  <!-- Progreso -->
                  <td>
                    <div class="progress-container">
                      <span class="progress-label">{{ getCalculatedProgress(reserva.status) }}%</span>
                      <div class="progress-bar-bg">
                        <div 
                          class="progress-bar-fill" 
                          [style.width.%]="getCalculatedProgress(reserva.status)"
                          [ngClass]="getProgressBarClass(reserva.status)"
                        ></div>
                      </div>
                    </div>
                  </td>

                  <!-- Acción -->
                  <td class="text-center">
                    <button class="action-btn" (click)="verDetalle(reserva.id)" title="Ver Detalle">
                      <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                        <circle cx="12" cy="12" r="3"/>
                      </svg>
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <footer class="pagination">
          <span class="muted" style="color: #64748b; font-size: 0.875rem;">
            {{ firstVisible() }}–{{ lastVisible() }} de {{ totalElements() }}
          </span>
          <div class="pagination-actions">
            <button class="btn-retry" style="padding: 6px 12px; font-size: 0.8rem;" type="button" (click)="loadPage(currentPage() - 1)" [disabled]="loading() || currentPage() === 0">
              Anterior
            </button>
            <span class="page-number" style="color: #475569; font-weight: 500; font-size: 0.875rem;">
              {{ currentPage() + 1 }} / {{ totalPages() }}
            </span>
            <button class="btn-retry" style="padding: 6px 12px; font-size: 0.8rem;" type="button" (click)="loadPage(currentPage() + 1)" [disabled]="loading() || currentPage() + 1 >= totalPages()">
              Siguiente
            </button>
          </div>
        </footer>
      }
    </div>
  `,
  styles: [`
    .booking-container {
      background-color: #ffffff;
      border-radius: 16px;
      padding: 32px;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
      max-width: 1100px;
      margin: 30px auto;
      font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    }

    .header-section {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
      padding-bottom: 16px;
      border-bottom: 1px solid #f0f0f0;
    }

    .header-section h2 {
      margin: 0;
      color: #0f172a;
      font-size: 1.5rem;
      font-weight: 700;
    }

    .subtitle {
      margin: 4px 0 0 0;
      color: #64748b;
      font-size: 0.875rem;
    }

    .btn-primary {
      background-color: #2563eb;
      color: #ffffff;
      text-decoration: none;
      padding: 10px 18px;
      border-radius: 8px;
      font-weight: 600;
      font-size: 0.875rem;
      transition: background-color 0.2s;
      display: inline-block;
    }

    .btn-primary:hover {
      background-color: #1d4ed8;
    }

    .booking-filters {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      margin-bottom: 24px;
      padding: 16px;
      background-color: #f8fafc;
      border-radius: 8px;
      align-items: center;
    }

    .filter-control, .page-size {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #475569;
      font-size: 0.875rem;
      font-weight: 500;
    }

    select {
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      padding: 6px 12px;
      background: #fff;
      color: #1e293b;
      font-size: 0.875rem;
      outline: none;
      transition: border-color 0.2s;
    }

    select:focus {
      border-color: #2563eb;
    }

    select:disabled {
      background-color: #f1f5f9;
      cursor: not-allowed;
    }

    /* Estados de Carga, Error y Vacío */
    .state-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 60px 20px;
      text-align: center;
    }

    .spinner {
      width: 40px;
      height: 40px;
      border: 3px solid #e2e8f0;
      border-top-color: #2563eb;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
      margin-bottom: 16px;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .empty-state .empty-icon-wrapper {
      width: 64px;
      height: 64px;
      border-radius: 50%;
      background-color: #f1f5f9;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 16px;
    }

    .state-icon {
      width: 32px;
      height: 32px;
      color: #64748b;
    }

    .error-icon {
      color: #ef4444;
      margin-bottom: 12px;
    }

    .empty-state h3, .error-state h3 {
      font-size: 1.25rem;
      color: #1e293b;
      margin: 0 0 8px 0;
    }

    .empty-state p, .error-state p {
      color: #64748b;
      max-width: 400px;
      margin: 0 0 20px 0;
      font-size: 0.95rem;
    }

    .btn-retry {
      padding: 8px 16px;
      background-color: #f1f5f9;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      color: #334155;
      font-weight: 600;
      cursor: pointer;
    }

    .btn-retry:hover:not(:disabled) {
      background-color: #e2e8f0;
    }

    .btn-retry:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    /* Tabla */
    .table-responsive {
      overflow-x: auto;
    }

    .booking-table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
    }

    .booking-table th {
      padding: 16px 12px;
      font-size: 0.75rem;
      font-weight: 700;
      color: #6c757d;
      letter-spacing: 0.05em;
      border-bottom: 1px solid #f0f0f0;
    }

    .table-row {
      border-bottom: 1px solid #f8f9fa;
      transition: background-color 0.2s ease;
    }

    .table-row:hover {
      background-color: #fafbfc;
    }

    .booking-table td {
      padding: 20px 12px;
      vertical-align: middle;
    }

    .service-cell {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    .service-icon {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      background-color: #e0f2fe;
      color: #0284c7;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .service-info {
      display: flex;
      flex-direction: column;
    }

    .service-title {
      font-weight: 700;
      font-size: 0.95rem;
      color: #1e293b;
    }

    .service-subtitle {
      font-size: 0.8rem;
      color: #64748b;
      margin-top: 2px;
    }

    .category-badge {
      display: inline-block;
      padding: 4px 12px;
      border-radius: 20px;
      background-color: #e0f2fe;
      color: #0284c7;
      font-size: 0.75rem;
      font-weight: 700;
      letter-spacing: 0.03em;
    }

    .status-container {
      display: inline-flex;
      align-items: center;
      font-weight: 600;
      font-size: 0.875rem;
      padding: 4px 10px;
      border-radius: 6px;
    }

    .status-active { color: #10b981; background-color: #ecfdf5; }
    .status-process { color: #f59e0b; background-color: #fffbeb; }
    .status-cancelled { color: #ef4444; background-color: #fef2f2; }
    .status-completed { color: #64748b; background-color: #f8fafc; }

    .session-date {
      color: #475569;
      font-weight: 500;
      font-size: 0.875rem;
    }

    .progress-container {
      display: flex;
      flex-direction: column;
      gap: 6px;
      width: 130px;
    }

    .progress-label {
      font-size: 0.8rem;
      font-weight: 600;
      color: #475569;
    }

    .progress-bar-bg {
      width: 100%;
      height: 6px;
      background-color: #e2e8f0;
      border-radius: 10px;
      overflow: hidden;
    }

    .progress-bar-fill {
      height: 100%;
      border-radius: 10px;
      transition: width 0.3s ease;
    }

    .bg-active { background-color: #10b981; }
    .bg-process { background-color: #f59e0b; }
    .bg-cancelled { background-color: #ef4444; }
    .bg-completed { background-color: #10b981; }

    .text-center { text-align: center; }

    .action-btn {
      width: 36px;
      height: 36px;
      border-radius: 8px;
      border: none;
      background-color: #eff6ff;
      color: #2563eb;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: all 0.2s ease;
    }

    .action-btn:hover {
      background-color: #dbeafe;
    }

    .pagination {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 24px;
      padding-top: 16px;
      border-top: 1px solid #f0f0f0;
    }

    .pagination-actions {
      display: flex;
      align-items: center;
      gap: 12px;
    }
  `]
})
export class MyBookingsComponent implements OnInit {
  private bookingService = inject(BookingService);

  reservas = signal<BookingSummary[]>([]);
  currentPage = signal<number>(0);
  pageSize = signal<number>(10);
  sort = signal<BookingSort>('DATE_DESC');
  activity = signal<BookingActivityFilter>('ALL');
  totalElements = signal<number>(0);
  totalPages = signal<number>(0);
  
  loading = signal<boolean>(true);
  error = signal<string | null>(null);

  firstVisible = computed(() => this.totalElements() === 0 ? 0 : this.currentPage() * this.pageSize() + 1);
  lastVisible = computed(() => Math.min((this.currentPage() + 1) * this.pageSize(), this.totalElements()));

  ngOnInit(): void {
    this.loadPage();
  }

  loadPage(page = this.currentPage()): void {
    this.loading.set(true);
    this.error.set(null);

    this.bookingService.listMine(page, this.pageSize(), this.sort(), this.activity()).subscribe({
      next: (result: BookingPage) => {
        this.reservas.set(result.content);
        this.currentPage.set(result.page);
        this.totalElements.set(result.totalElements);
        this.totalPages.set(result.totalPages);
        this.loading.set(false);
      },
      error: (err) => {
        const errorMsg = err?.error?.detail || err?.message || 'No fue posible cargar tus reservas.';
        this.error.set(errorMsg);
        this.loading.set(false);
      }
    });
  }

  changePageSize(size: number): void {
    this.pageSize.set(Number(size));
    this.loadPage(0);
  }

  changeSort(sort: BookingSort): void {
    this.sort.set(sort);
    this.loadPage(0);
  }

  changeActivity(activity: BookingActivityFilter): void {
    this.activity.set(activity);
    this.loadPage(0);
  }

  getStatusClass(estado: string): string {
    switch (estado) {
      case 'Activo':
      case 'CONFIRMED':
        return 'status-active';
      case 'En Proceso':
      case 'CREATED':
        return 'status-process';
      case 'Cancelado':
      case 'CANCELLED':
        return 'status-cancelled';
      case 'Completado':
      case 'COMPLETED':
        return 'status-completed';
      default:
        return '';
    }
  }

  getStatusLabel(estado: string): string {
    switch (estado) {
      case 'CREATED': return 'En Proceso';
      case 'CONFIRMED': return 'Confirmada';
      case 'CANCELLED': return 'Cancelada';
      case 'COMPLETED': return 'Completada';
      default: return estado || 'Pendiente';
    }
  }

  getProgressBarClass(estado: string): string {
    switch (estado) {
      case 'Activo':
      case 'CONFIRMED': return 'bg-active';
      case 'En Proceso':
      case 'CREATED': return 'bg-process';
      case 'Cancelado':
      case 'CANCELLED': return 'bg-cancelled';
      case 'Completado':
      case 'COMPLETED': return 'bg-completed';
      default: return 'bg-active';
    }
  }

  getCalculatedProgress(estado: string): number {
    switch (estado) {
      case 'CREATED': return 25;
      case 'CONFIRMED': return 50;
      case 'COMPLETED': return 100;
      case 'CANCELLED': return 0;
      default: return 50;
    }
  }

  formatearFecha(fechaIso?: string): string {
    if (!fechaIso) return 'Pendiente';
    const date = new Date(fechaIso);
    return isNaN(date.getTime()) 
      ? fechaIso 
      : date.toLocaleDateString('es-ES', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
  }

  verDetalle(id: string): void {
    console.log('Detalle de reserva:', id);
  }
}