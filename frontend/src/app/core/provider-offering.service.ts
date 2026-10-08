import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiBase } from './api';

export type OfferingStatus = 'ACTIVE' | 'INACTIVE';
export type ProviderOfferingSort = 'NAME_ASC' | 'NAME_DESC' | 'CREATED_ASC' | 'CREATED_DESC';

export interface ProviderOffering {
    id: string;
    code: string;
    name: string;
    categoryId: string;
    price: number;
    shortDescription: string | null;
    detail: string | null;
    learningObjectives: string | null;
    prerequisites: string | null;
    capacity: number | null;
    status: OfferingStatus;
    createdBy: string;
}

export interface ProviderOfferingPage {
    content: ProviderOffering[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface ProviderOfferingRequest {
    name: string;
    categoryId: string;
    price: number;
    shortDescription: string | null;
    detail: string | null;
    learningObjectives: string | null;
    prerequisites: string | null;
    capacity: number | null;
}

@Injectable({ providedIn: 'root' })
export class ProviderOfferingService {
    private readonly http = inject(HttpClient);
    private readonly url = `${apiBase()}/provider/offerings`;

    listMine(page: number, size: number, sort: ProviderOfferingSort): Observable<ProviderOfferingPage> {
        const params = new HttpParams()
            .set('page', page)
            .set('size', size)
            .set('sort', sort);
        return this.http.get<ProviderOfferingPage>(this.url, { params });
    }

    create(request: ProviderOfferingRequest): Observable<ProviderOffering> {
        return this.http.post<ProviderOffering>(this.url, request);
    }

    update(id: string, request: ProviderOfferingRequest): Observable<ProviderOffering> {
        return this.http.put<ProviderOffering>(`${this.url}/${id}`, request);
    }

    changeStatus(id: string, status: OfferingStatus): Observable<ProviderOffering> {
        return this.http.patch<ProviderOffering>(`${this.url}/${id}/status`, { status });
    }
}