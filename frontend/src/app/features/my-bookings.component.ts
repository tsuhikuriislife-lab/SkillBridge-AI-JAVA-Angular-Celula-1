import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BookingActivityFilter, BookingPage, BookingService, BookingSort, BookingSummary } from '../core/booking.service';

@Component({
    standalone: true,
    imports: [CurrencyPipe, DatePipe, FormsModule],
    template: `
    <section class="container bookings-page">
      <header class="page-heading">
        <div>
          <p class="eyebrow">TU ACTIVIDAD</p>
          <h1>Mis reservas</h1>
        </div>
        <label class="page-size">
          Mostrar
          <select [ngModel]="pageSize" (ngModelChange)="changePageSize($event)" [disabled]="loading">
            <option [ngValue]="10">10</option>
            <option [ngValue]="25">25</option>
            <option [ngValue]="50">50</option>
          </select>
          por página
        </label>
      </header>

      <section class="booking-filters" aria-label="Filtros de reservas">
        <label class="filter-control">
          Ordenar por
          <select [ngModel]="sort" (ngModelChange)="changeSort($event)" [disabled]="loading">
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
          <select [ngModel]="activity" (ngModelChange)="changeActivity($event)" [disabled]="loading">
            <option value="ALL">Todas</option>
            <option value="ACTIVE">Activas</option>
            <option value="INACTIVE">No activas</option>
          </select>
        </label>
      </section>

      @if (error) {
        <div class="message error" role="alert">
          <span>{{ error }}</span>
          <button class="text-button" type="button" (click)="loadPage()" [disabled]="loading">Reintentar</button>
        </div>
      }

      @if (loading) {
        <p class="message muted" role="status">Cargando reservas...</p>
      } @else if (!error && bookings.length === 0) {
        <div class="empty-state">
          @if (totalElements === 0 && activity === 'ALL') {
            <h2>Aún no tienes reservas</h2>
            <p class="muted">Cuando reserves una mentoría, aparecerá aquí.</p>
          } @else {
            <h2>No hay reservas que coincidan</h2>
            <p class="muted">Prueba con otro estado o criterio de orden.</p>
          }
        </div>
      } @else if (!error) {
        <div class="booking-list" aria-live="polite">
          @for (booking of bookings; track booking.id) {
            <article class="booking-row">
              <div class="booking-main">
                <h2>{{ booking.offeringTitle }}</h2>
                <p class="muted">{{ booking.scheduledAt | date:'medium' }}</p>
              </div>
              <span class="status" [class.status-active]="booking.active" [class.status-inactive]="!booking.active">
                {{ statusLabel(booking.status) }}
              </span>
              <span class="booking-price">{{ booking.price | currency:'COP':'symbol-narrow':'1.0-0':'es-CO' }}</span>
              <span class="booking-id">{{ booking.id }}</span>
            </article>
          }
        </div>

        <footer class="pagination">
          <span class="muted">
            {{ firstVisible }}–{{ lastVisible }} de {{ totalElements }}
          </span>
          <div class="pagination-actions">
            <button class="btn secondary" type="button" (click)="loadPage(currentPage - 1)" [disabled]="loading || currentPage === 0">
              Anterior
            </button>
            <span class="page-number">{{ currentPage + 1 }} / {{ totalPages }}</span>
            <button class="btn secondary" type="button" (click)="loadPage(currentPage + 1)" [disabled]="loading || currentPage + 1 >= totalPages">
              Siguiente
            </button>
          </div>
        </footer>
      }
    </section>
  `,
    styles: [`
    .bookings-page{padding:42px 0 64px;max-width:900px}
    .page-heading{display:flex;align-items:end;justify-content:space-between;gap:20px;margin-bottom:24px}
    .eyebrow{font-size:12px;font-weight:800;letter-spacing:.12em;color:#397367;margin:0 0 8px}
    h1{font-size:30px;margin:0;color:#17332d}
    .page-size{display:flex;align-items:center;gap:8px;color:#475467;font-size:14px}
    select{border:1px solid #cfd8e6;border-radius:6px;padding:8px;background:#fff;color:#17332d}
    .booking-filters{display:flex;flex-wrap:wrap;gap:16px;margin-bottom:22px;padding:14px 0;border-top:1px solid #dfe7e4;border-bottom:1px solid #dfe7e4}
    .filter-control{display:grid;gap:6px;color:#475467;font-size:13px}
    .booking-list{border-top:1px solid #dfe7e4}
    .booking-row{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px 20px;padding:20px 4px;border-bottom:1px solid #dfe7e4;align-items:center}
    .booking-main h2{font-size:17px;margin:0 0 5px;color:#17332d}
    .booking-main p{margin:0;font-size:14px}
    .status{font-size:12px;font-weight:700;color:#475467;background:#eef2f1;padding:6px 9px;border-radius:4px}
    .status-active{color:#176b4d;background:#e7f4ec}
    .status-inactive{color:#8c3d27;background:#faeee8}
    .booking-price{grid-column:2;color:#17332d;font-size:14px;font-weight:700;text-align:right}
    .booking-id{grid-column:1 / -1;color:#667085;font:12px ui-monospace,monospace;overflow-wrap:anywhere}
    .pagination{display:flex;justify-content:space-between;align-items:center;gap:18px;padding-top:18px;font-size:14px}
    .pagination-actions{display:flex;align-items:center;gap:12px}
    .page-number{min-width:52px;text-align:center;color:#344054}
    .btn{padding:8px 12px;border-radius:6px}
    .btn:disabled{cursor:not-allowed;opacity:.48}
    .empty-state{padding:44px 4px;border-top:1px solid #dfe7e4;border-bottom:1px solid #dfe7e4}
    .empty-state h2{margin:0 0 8px;font-size:20px;color:#17332d}
    .empty-state p{margin:0}
    .message{padding:16px 4px}
    .error{color:#b42318;display:flex;justify-content:space-between;gap:12px;align-items:center}
    .text-button{border:0;background:none;color:#9f241b;text-decoration:underline;cursor:pointer}
    @media(max-width:600px){
      .bookings-page{padding-top:28px}
      .page-heading{align-items:start;flex-direction:column}
      .booking-row{gap:10px}
      .pagination{align-items:start;flex-direction:column}
      .pagination-actions{width:100%;justify-content:space-between}
    }
  `]
})
export class MyBookingsComponent implements OnInit {
    bookings: BookingSummary[] = [];
    currentPage = 0;
    pageSize = 10;
    sort: BookingSort = 'DATE_DESC';
    activity: BookingActivityFilter = 'ALL';
    totalElements = 0;
    totalPages = 0;
    loading = false;
    error = '';

    constructor(private bookingService: BookingService) { }

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
        this.bookingService.listMine(page, this.pageSize, this.sort, this.activity).subscribe({
            next: (result: BookingPage) => {
                this.bookings = result.content;
                this.currentPage = result.page;
                this.totalElements = result.totalElements;
                this.totalPages = result.totalPages;
                this.loading = false;
            },
            error: () => {
                this.error = 'No fue posible cargar tus reservas.';
                this.loading = false;
            }
        });
    }

    changePageSize(size: number): void {
        this.pageSize = Number(size);
        this.loadPage(0);
    }

    changeSort(sort: BookingSort): void {
        this.sort = sort;
        this.loadPage(0);
    }

    changeActivity(activity: BookingActivityFilter): void {
        this.activity = activity;
        this.loadPage(0);
    }

    statusLabel(status: string): string {
        const labels: Record<string, string> = {
            CREATED: 'Creada',
            CONFIRMED: 'Confirmada',
            CANCELLED: 'Cancelada',
            COMPLETED: 'Completada'
        };
        return labels[status] ?? status;
    }
}