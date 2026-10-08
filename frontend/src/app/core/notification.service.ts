import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { apiBase } from './api';

export interface NotificationSummary {
    id: string;
    type: string;
    message: string;
    status: string;
    createdAt: string;
    readAt?: string;
    title?: string;
    bookingId?: string;
}

export interface NotificationPage {
    content: NotificationSummary[];
    page?: number;
    number?: number;
    size?: number;
    totalElements: number;
    totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
    private readonly http = inject(HttpClient);

    // Backend implementation was deleted in fix/crud-completo branch.
    // Mocking to avoid breaking the frontend.
    listMine(page: number, size: number, unreadOnly?: boolean): Observable<NotificationPage> {
        let params = new HttpParams()
            .set('page', page.toString())
            .set('size', size.toString());
        return this.http.get<NotificationPage>(`${apiBase()}/history/me`, { params });
    }

    markAsRead(id: string): Observable<void> {
        return of(void 0);
    }
}
