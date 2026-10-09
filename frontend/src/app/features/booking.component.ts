import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Offering, OfferingService } from '../core/offering.service';
import { BookingService } from '../core/booking.service';

@Component({
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="container section">
      <div class="card booking-card">
        <p class="eyebrow">NUEVA INSCRIPCIÓN</p>
        <h1>Inscribirse a un servicio</h1>
        <p class="muted">Al confirmar, quedarás inscrito en el servicio.</p>

        <label class="field">
          Servicio
          <select [(ngModel)]="offeringId">
            <option value="" disabled>Selecciona un servicio</option>
            @for (offering of offerings; track offering.id) {
              <option [value]="offering.id">{{ offering.title }}</option>
            }
          </select>
        </label>

        <button class="btn" [disabled]="loading" (click)="book()">
          {{ loading ? 'Inscribiendo...' : 'Confirmar Inscripción' }}
        </button>

        @if (error) { <p class="error">{{ error }}</p> }
        @if (success) { <p class="success">{{ success }}</p> }
      </div>
    </section>
  `,
  styles: [`.section{padding:50px 0}.booking-card{max-width:700px;margin:auto}.eyebrow{font-weight:800;letter-spacing:.1em}.field select{border:1px solid #cfd8e6;border-radius:10px;padding:11px;background:white}`]
})
export class BookingComponent implements OnInit {
  offerings: Offering[] = [];
  offeringId = '';
  loading = false;
  error = '';
  success = '';

  constructor(
    private offeringsService: OfferingService,
    private bookingService: BookingService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    const qOfferingId = this.route.snapshot.queryParamMap.get('offeringId');
    if (qOfferingId) {
      this.offeringId = qOfferingId;
    }

    this.offeringsService.list().subscribe({
      next: list => {
        this.offerings = list;
        if (qOfferingId) {
          this.offeringId = qOfferingId;
        }
      },
      error: () => this.error = 'No fue posible cargar los servicios.'
    });
  }

  book(): void {
    this.error = '';
    this.success = '';
    if (!this.offeringId) {
      this.error = 'Selecciona un servicio.';
      return;
    }

    this.loading = true;
    this.bookingService.create({ offeringId: this.offeringId, scheduledAt: '' })
      .subscribe({
        next: () => {
          this.success = 'Inscripción creada exitosamente.';
          this.loading = false;
        },
        error: e => {
          this.error = e?.error?.detail || 'No fue posible crear la inscripción. Verifica tu sesión.';
          this.loading = false;
        }
      });
  }
}
