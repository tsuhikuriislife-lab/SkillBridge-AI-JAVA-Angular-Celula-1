import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { apiBase } from './api';
import { AuthService } from './auth.service';

export interface NotificationSummary {
    id: string;
    type: string;
    message: string;
    status: string;
    createdAt: string;
    readAt?: string;
    title?: string;
    bookingId?: string;
    targetId?: string;
    targetType?: string;
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
    private readonly auth = inject(AuthService);

    // Backend implementation was deleted in fix/crud-completo branch.
    // Mocking to avoid breaking the frontend.
    listMine(page: number, size: number, unreadOnly?: boolean): Observable<NotificationPage> {
        let params = new HttpParams()
            .set('page', page.toString())
            .set('size', size.toString());
        const endpoint = this.auth.role() === 'ADMIN' ? '/admin/notifications' : '/history/me';
        return this.http.get<NotificationPage>(`${apiBase()}${endpoint}`, { params });
    }

    markAsRead(id: string): Observable<void> {
        return of(void 0);
    }
}
