import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiBase } from './api';
import { Booking } from './models/booking.model';

@Injectable({
  providedIn: 'root'
})
export class BookingService {
  private http = inject(HttpClient);

  /**
   * Obtiene las reservas del cliente autenticado según su token JWT.
   */
  getMyBookings(): Observable<Booking[]> {
    return this.http.get<Booking[]>(`${apiBase()}/bookings/me`);
  }
}