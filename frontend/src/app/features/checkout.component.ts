import { Component, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { BookingService } from '../core/booking.service';
import { OfferingService, Offering } from '../core/offering.service';
import { apiBase } from '../core/api';

import { ToastService } from '../core/toast.service';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CommonModule, CurrencyPipe],
  templateUrl: './checkout.component.html',
  styleUrls: ['./checkout.component.css']
})
export class CheckoutComponent implements OnInit {
  offeringId: string | null = null;
  offering: Offering | null = null;
  instructorName: string = 'N/A';
  scheduleText: string = 'Próximamente';
  
  currentStep = 2; // Default to 'Pago' active step as in the design
  selectedMethod: 'tarjeta' | 'transferencia' | 'paypal' = 'tarjeta';
  isPaid = false;
  isProcessing = false;
  error = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private bookingService: BookingService,
    private offeringService: OfferingService,
    private http: HttpClient,
    private toastService: ToastService
  ) {}

  ngOnInit(): void {
    this.offeringId = this.route.snapshot.paramMap.get('id');
    if (this.offeringId) {
      this.http.get<any>(`${apiBase()}/enrollments/me?size=100`).subscribe({
        next: (res) => {
          const list = res.content || [];
          const isEnrolled = list.some((e: any) => e.serviceId === this.offeringId && e.status === 'ACTIVE');
          if (isEnrolled) {
            this.handleAlreadyEnrolled();
          } else {
            this.loadServiceData();
          }
        },
        error: () => this.loadServiceData()
      });
    } else {
      this.error = 'ID de servicio no proporcionado.';
    }
  }

  private handleAlreadyEnrolled(): void {
    this.error = 'Ya estás inscrito en este servicio.';
    this.toastService.show('Ya tienes una reserva activa para este servicio.', [
      {
        label: 'Regresar al servicio',
        primary: true,
        action: () => {
          this.toastService.clear();
          this.router.navigate(['/service', this.offeringId]);
        }
      }
    ]);
  }

  private loadServiceData(): void {
    if (!this.offeringId) return;
    this.offeringService.getById(this.offeringId).subscribe({
      next: (data) => {
        this.offering = data;
        this.loadInstructor(data.createdBy);
        this.loadSchedule(data.id);
      },
      error: () => {
        this.error = 'No se pudo cargar el servicio a reservar.';
      }
    });
  }

  private loadInstructor(userId?: string) {
    if (!userId) return;
    this.http.get<any>(`${apiBase()}/users/${userId}`).subscribe({
      next: (u) => this.instructorName = u.name,
      error: () => this.instructorName = 'N/A'
    });
  }

  private loadSchedule(offeringId: string) {
    this.http.get<any[]>(`${apiBase()}/services/${offeringId}/schedules`).subscribe({
      next: (schedules) => {
        if (schedules && schedules.length > 0) {
          const s = schedules[0];
          this.scheduleText = `${s.startDay}s - ${s.sessionDuration} horas/sesión`;
        }
      },
      error: () => this.scheduleText = 'N/A'
    });
  }

  selectMethod(method: 'tarjeta' | 'transferencia' | 'paypal'): void {
    this.selectedMethod = method;
  }

  pay(): void {
    if (!this.offeringId) return;
    
    this.isProcessing = true;
    this.error = '';
    
    const nextWeek = new Date();
    nextWeek.setDate(nextWeek.getDate() + 7);

    this.bookingService.create({
      offeringId: this.offeringId,
      scheduledAt: nextWeek.toISOString()
    }).subscribe({
      next: () => {
        this.isPaid = true;
        this.currentStep = 3;
        this.isProcessing = false;
      },
      error: (err) => {
        const msg = err.error?.detail || err.error?.message || 'Ocurrió un error al procesar el pago y la reserva.';
        this.error = msg;
        this.isProcessing = false;
        console.error(err);
      }
    });
  }

  formatCardNumber(event: Event): void {
    const input = event.target as HTMLInputElement;
    const digits = input.value.replace(/\D/g, '').slice(0, 16);
    input.value = digits.replace(/(.{4})/g, '$1 ').trim();
  }

  formatCardExp(event: Event): void {
    const input = event.target as HTMLInputElement;
    const digits = input.value.replace(/\D/g, '').slice(0, 4);
    input.value = digits.length > 2 ? digits.slice(0, 2) + '/' + digits.slice(2) : digits;
  }
}
