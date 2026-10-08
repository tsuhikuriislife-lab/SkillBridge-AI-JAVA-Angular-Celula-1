import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { BookingService, BookingSummary, BookingSort, BookingActivityFilter, BookingPage } from '../../core/booking.service';


@Component({
  selector: 'app-display-bookings',
  imports: [CommonModule, FormsModule, CurrencyPipe, RouterLink],
  templateUrl: './display-bookings.html',
  styleUrl: './display-bookings.css',
})

export class DisplayBookingsComponent implements OnInit {
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