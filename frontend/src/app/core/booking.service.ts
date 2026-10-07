import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { apiBase } from './api';
import { Booking } from './models/booking.model';

export interface BookingSummary {
    id: string;
    offeringId: string;
    offeringTitle: string;
    price: number;
    scheduledAt: string;
    status: string;
    active: boolean;
}

export type BookingSort = 'TITLE_ASC' | 'TITLE_DESC' | 'DATE_ASC' | 'DATE_DESC' | 'PRICE_ASC' | 'PRICE_DESC';
export type BookingActivityFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';

export interface BookingPage {
    content: BookingSummary[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class BookingService {
    private readonly http = inject(HttpClient);

    listMine(page: number, size: number, sort: BookingSort, activity: BookingActivityFilter): Observable<BookingPage> {
        const params = new HttpParams()
            .set('page', page)
            .set('size', size)
            .set('sort', sort)
            .set('activity', activity);
        return this.http.get<BookingPage>(`${apiBase()}/bookings/me`, { params });
    }

    getMyBookings(): Observable<Booking[]> {
        // Return first 50 bookings mapped to the Booking interface for the component
        return this.listMine(0, 50, 'DATE_DESC', 'ALL').pipe(
            map(page => page.content.map(summary => ({
                id: summary.id,
                offeringId: summary.offeringId,
                status: summary.status as any,
                scheduledAt: summary.scheduledAt,
                serviceTitle: summary.offeringTitle,
                category: 'GENERAL' // mock
            })))
        );
    }
}
