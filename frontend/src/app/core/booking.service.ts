import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, forkJoin, of } from 'rxjs';
import { map, switchMap, catchError } from 'rxjs/operators';
import { apiBase } from './api';
import { OfferingService } from './offering.service';

export interface BookingSummary {
    id: string; // The serviceId
    offeringId: string; // The serviceId
    offeringTitle: string;
    serviceCode?: string;
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
        const params = new HttpParams()
            .set('page', '0')
            .set('size', '100');
            
        return this.http.get<any>(`${apiBase()}/enrollments/me`, { params }).pipe(
            switchMap(pageResult => {
                const enrollments = pageResult.content || [];
                if (enrollments.length === 0) {
                    return of({
                        content: [],
                        page: 0,
                        size: size,
                        totalElements: 0,
                        totalPages: 0
                    });
                }
                
                // Fetch service details for each enrollment
                const detailRequests = enrollments.map((enrollment: any) => 
                    this.offeringService.getById(enrollment.serviceId).pipe(
                        map(service => ({
                            id: enrollment.serviceId,
                            offeringId: enrollment.serviceId,
                            offeringTitle: service.title,
                            serviceCode: service.code,
                            price: service.price,
                            scheduledAt: enrollment.startDate,
                            status: enrollment.status,
                            active: enrollment.status === 'ACTIVE'
                        })),
                        catchError(() => of({
                            id: enrollment.serviceId,
                            offeringId: enrollment.serviceId,
                            offeringTitle: 'Servicio #' + enrollment.serviceId.substring(0, 8),
                            serviceCode: 'SRV-' + enrollment.serviceId.substring(0, 4).toUpperCase(),
                            price: 0,
                            scheduledAt: enrollment.startDate,
                            status: enrollment.status,
                            active: enrollment.status === 'ACTIVE'
                        }))
                    )
                );
                
                return forkJoin(detailRequests).pipe(
                    map(summaries => {
                        let filtered = summaries as BookingSummary[];

                        // Filtrado por actividad (ALL / ACTIVE / INACTIVE)
                        if (activity === 'ACTIVE') {
                            filtered = filtered.filter(item => item.status === 'ACTIVE' || item.active);
                        } else if (activity === 'INACTIVE') {
                            filtered = filtered.filter(item => item.status !== 'ACTIVE' && !item.active);
                        }

                        // Ordenamiento
                        if (sort === 'TITLE_ASC') {
                            filtered.sort((a, b) => (a.offeringTitle || '').localeCompare(b.offeringTitle || ''));
                        } else if (sort === 'TITLE_DESC') {
                            filtered.sort((a, b) => (b.offeringTitle || '').localeCompare(a.offeringTitle || ''));
                        } else if (sort === 'DATE_ASC') {
                            filtered.sort((a, b) => new Date(a.scheduledAt || 0).getTime() - new Date(b.scheduledAt || 0).getTime());
                        } else if (sort === 'DATE_DESC') {
                            filtered.sort((a, b) => new Date(b.scheduledAt || 0).getTime() - new Date(a.scheduledAt || 0).getTime());
                        } else if (sort === 'PRICE_ASC') {
                            filtered.sort((a, b) => (a.price || 0) - (b.price || 0));
                        } else if (sort === 'PRICE_DESC') {
                            filtered.sort((a, b) => (b.price || 0) - (a.price || 0));
                        }

                        // Paginación en cliente sobre el conjunto filtrado
                        const totalElements = filtered.length;
                        const totalPages = Math.max(1, Math.ceil(totalElements / size));
                        const safePage = Math.min(page, Math.max(0, totalPages - 1));
                        const startIndex = safePage * size;
                        const pageContent = filtered.slice(startIndex, startIndex + size);

                        return {
                            content: pageContent,
                            page: safePage,
                            size: size,
                            totalElements: totalElements,
                            totalPages: totalPages
                        };
                    })
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