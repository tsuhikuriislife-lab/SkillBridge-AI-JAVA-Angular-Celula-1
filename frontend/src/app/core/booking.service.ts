import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, forkJoin, of } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';
import { apiBase } from './api';
import { OfferingService } from './offering.service';

export interface BookingSummary {
    id: string; // The serviceId
    offeringId: string; // The serviceId
    offeringTitle: string;
    price: number;
    scheduledAt: string; // startDate
    status: string; // ACTIVE, COMPLETED, CANCELLED
    active: boolean; // status == 'ACTIVE'
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
    private readonly offeringService = inject(OfferingService);

    listMine(page: number, size: number, sort: BookingSort, activity: BookingActivityFilter): Observable<BookingPage> {
        // The new API doesn't support sorting and activity filtering directly in the same way,
        // it just takes page and size. We'll pass them but ignore sort/activity for now.
        const params = new HttpParams()
            .set('page', page)
            .set('size', size);
            
        return this.http.get<any>(`${apiBase()}/enrollments/me`, { params }).pipe(
            switchMap(pageResult => {
                const enrollments = pageResult.content || [];
                if (enrollments.length === 0) {
                    return of({
                        content: [],
                        page: pageResult.number,
                        size: size,
                        totalElements: pageResult.totalElements,
                        totalPages: pageResult.totalPages
                    });
                }
                
                // Fetch service details for each enrollment
                const detailRequests = enrollments.map((enrollment: any) => 
                    this.offeringService.getById(enrollment.serviceId).pipe(
                        map(service => ({
                            id: enrollment.serviceId,
                            offeringId: enrollment.serviceId,
                            offeringTitle: service.title,
                            price: service.price,
                            scheduledAt: enrollment.startDate,
                            status: enrollment.status,
                            active: enrollment.status === 'ACTIVE'
                        }))
                    )
                );
                
                return forkJoin(detailRequests).pipe(
                    map(summaries => ({
                        content: summaries as BookingSummary[],
                        page: pageResult.number,
                        size: size,
                        totalElements: pageResult.totalElements,
                        totalPages: pageResult.totalPages
                    }))
                );
            })
        );
    }

    create(booking: { offeringId: string, scheduledAt: string }): Observable<any> {
        return this.http.post<any>(`${apiBase()}/services/${booking.offeringId}/enroll`, {});
    }

    cancel(serviceId: string): Observable<any> {
        return this.http.delete<any>(`${apiBase()}/enrollments/${serviceId}`);
    }
}