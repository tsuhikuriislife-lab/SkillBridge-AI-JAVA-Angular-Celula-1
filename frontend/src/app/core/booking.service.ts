import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiBase } from './api';

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

    create(booking: { offeringId: string, scheduledAt: string }): Observable<any> {
        return this.http.post<any>(`${apiBase()}/bookings`, booking);
    }
}