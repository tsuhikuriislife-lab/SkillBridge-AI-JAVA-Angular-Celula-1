import { Component, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { BookingService } from '../../core/booking.service';
import { OfferingService, Offering } from '../../core/offering.service';

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
  
  currentStep = 2; // Default to 'Pago' active step as in the design
  selectedMethod: 'tarjeta' | 'transferencia' | 'paypal' = 'tarjeta';
  isPaid = false;
  isProcessing = false;
  error = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private bookingService: BookingService,
    private offeringService: OfferingService
  ) {}

  ngOnInit(): void {
    this.offeringId = this.route.snapshot.paramMap.get('id');
    if (this.offeringId) {
      this.offeringService.getById(this.offeringId).subscribe({
        next: (data) => {
          this.offering = data;
        },
        error: () => {
          this.error = 'No se pudo cargar el servicio a reservar.';
        }
      });
    } else {
      this.error = 'ID de servicio no proporcionado.';
    }
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
        this.error = 'Ocurrió un error al procesar el pago y la reserva.';
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
