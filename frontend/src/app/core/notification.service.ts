import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiBase } from './api';

export type NotificationStatus = 'PENDING' | 'PROCESSED' | 'FAILED';
export type NotificationType = 'IN_APP';

export interface NotificationSummary {
    id: string;
    bookingId: string;
    eventType: string;
    type: NotificationType;
    status: NotificationStatus;
    title: string;
    message: string;
    createdAt: string;
    processedAt: string | null;
}

export interface NotificationPage {
    content: NotificationSummary[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
    private readonly http = inject(HttpClient);

    listMine(page: number, size: number): Observable<NotificationPage> {
        const params = new HttpParams()
            .set('page', page)
            .set('size', size);
        return this.http.get<NotificationPage>(`${apiBase()}/notifications/me`, { params });
    }
}
