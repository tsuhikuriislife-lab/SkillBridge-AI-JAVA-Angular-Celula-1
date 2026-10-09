import { DatePipe } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { NotificationPage, NotificationService, NotificationSummary } from '../core/notification.service';

@Component({
    standalone: true,
    imports: [DatePipe],
    template: `
    <section class="container notifications-page">
      <header class="page-heading">
        <div>
          <p class="eyebrow">TU ACTIVIDAD</p>
          <h1>Mis notificaciones</h1>
          <p class="muted">Consulta el resultado del procesamiento de las notificaciones de tus reservas.</p>
        </div>
        <button class="btn secondary" type="button" (click)="loadPage()" [disabled]="loading">
          Actualizar
        </button>
      </header>

      @if (error) {
        <div class="message error" role="alert">
          <span>{{ error }}</span>
          <button class="text-button" type="button" (click)="loadPage()" [disabled]="loading">Reintentar</button>
        </div>
      }

      @if (loading) {
        <p class="message muted" role="status">Cargando notificaciones...</p>
      } @else if (!error && notifications.length === 0) {
        <div class="empty-state">
          <h2>Aún no tienes notificaciones</h2>
          <p class="muted">Cuando se procese una reserva, su notificación aparecerá aquí.</p>
        </div>
      } @else if (!error) {
        <div class="notification-list" aria-live="polite">
          @for (notification of notifications; track notification.id) {
            <article class="notification-row">
              <div class="notification-main">
                <h2>{{ notification.title }}</h2>
                <p>{{ notification.message }}</p>
                <p class="muted">Reserva: {{ notification.bookingId }}</p>
                <p class="muted">Recibida: {{ notification.createdAt | date:'medium' }}</p>
              </div>
              <span class="status" [class.status-processed]="notification.status === 'PROCESSED'"
                    [class.status-failed]="notification.status === 'FAILED'">
                {{ statusLabel(notification.status) }}
              </span>
            </article>
          }
        </div>

        <footer class="pagination">
          <span class="muted">{{ firstVisible }}–{{ lastVisible }} de {{ totalElements }}</span>
          <div class="pagination-actions">
            <button class="btn secondary" type="button" (click)="loadPage(currentPage - 1)"
                    [disabled]="loading || currentPage === 0">Anterior</button>
            <span class="page-number">{{ currentPage + 1 }} / {{ totalPages }}</span>
            <button class="btn secondary" type="button" (click)="loadPage(currentPage + 1)"
                    [disabled]="loading || currentPage + 1 >= totalPages">Siguiente</button>
          </div>
        </footer>
      }
    </section>
  `,
    styles: [`
    .notifications-page{padding:42px 0 64px;max-width:900px}
    .page-heading{display:flex;align-items:end;justify-content:space-between;gap:20px;margin-bottom:24px}
    .eyebrow{font-size:12px;font-weight:800;letter-spacing:.12em;color:#397367;margin:0 0 8px}
    h1{font-size:30px;margin:0 0 8px;color:#17332d}
    .muted{color:#667085}
    .notification-list{border-top:1px solid #dfe7e4}
    .notification-row{display:flex;justify-content:space-between;align-items:start;gap:18px;padding:20px 4px;border-bottom:1px solid #dfe7e4}
    .notification-main h2{font-size:17px;margin:0 0 7px;color:#17332d}
    .notification-main p{margin:4px 0}
    .status{white-space:nowrap;font-size:12px;font-weight:700;color:#475467;background:#eef2f1;padding:6px 9px;border-radius:4px}
    .status-processed{color:#176b4d;background:#e7f4ec}
    .status-failed{color:#8c3d27;background:#faeee8}
    .pagination{display:flex;justify-content:space-between;align-items:center;gap:18px;padding-top:18px;font-size:14px}
    .pagination-actions{display:flex;align-items:center;gap:12px}
    .page-number{min-width:52px;text-align:center;color:#344054}
    .btn{padding:8px 12px;border-radius:6px}
    .btn:disabled{cursor:not-allowed;opacity:.48}
    .empty-state{padding:44px 4px;border-top:1px solid #dfe7e4;border-bottom:1px solid #dfe7e4}
    .empty-state h2{margin:0 0 8px;font-size:20px;color:#17332d}
    .message{padding:16px 4px}
    .error{color:#b42318;display:flex;justify-content:space-between;gap:12px;align-items:center}
    .text-button{border:0;background:none;color:#9f241b;text-decoration:underline;cursor:pointer}
    @media(max-width:600px){
      .notifications-page{padding-top:28px}
      .page-heading{align-items:start;flex-direction:column}
      .notification-row{align-items:start;flex-direction:column}
      .pagination{align-items:start;flex-direction:column}
      .pagination-actions{width:100%;justify-content:space-between}
    }
  `]
})
export class MyNotificationsComponent implements OnInit {
    private readonly pageSize = 20;
    notifications: NotificationSummary[] = [];
    currentPage = 0;
    totalElements = 0;
    totalPages = 0;
    loading = false;
    error = '';

    constructor(private readonly notificationService: NotificationService) {}

    get firstVisible(): number {
        return this.totalElements === 0 ? 0 : this.currentPage * this.pageSize + 1;
    }

    get lastVisible(): number {
        return Math.min((this.currentPage + 1) * this.pageSize, this.totalElements);
    }

    ngOnInit(): void {
        this.loadPage();
    }

    loadPage(page = this.currentPage): void {
        this.loading = true;
        this.error = '';
        this.notificationService.listMine(page, this.pageSize).subscribe({
            next: (result: NotificationPage) => {
                this.notifications = result.content;
                this.currentPage = result.number !== undefined ? result.number : (result.page ?? 0);
                this.totalElements = result.totalElements;
                this.totalPages = result.totalPages;
                this.loading = false;
            },
            error: () => {
                this.error = 'No fue posible cargar tus notificaciones.';
                this.loading = false;
            }
        });
    }

    statusLabel(status: string): string {
        const labels: Record<string, string> = {
            PENDING: 'Pendiente',
            PROCESSED: 'Procesada',
            FAILED: 'Fallida'
        };
        return labels[status] ?? status;
    }
}
