import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { apiBase } from './api';

export interface Offering {
  id: string;
  title: string;
  description: string;
  category: string;
  price: number;
  active: boolean;
  startTime?: string;
  endTime?: string;
  endDay?: string;
  photoUrl?: string;
}

export interface PageResult<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
}

export interface CategoryCount {
  name: string;
  count: number;
}

@Injectable({ providedIn: 'root' })
export class OfferingService {
  constructor(private http: HttpClient) {}
  list() { return this.http.get<Offering[]>(`${apiBase()}/offerings`); }
  
  getById(id: string) { return this.http.get<Offering>(`${apiBase()}/offerings/${id}`); }

  getCategories() { return this.http.get<string[]>(`${apiBase()}/categories`); }
  getTopCategories(limit: number = 8) { return this.http.get<CategoryCount[]>(`${apiBase()}/categories/top?limit=${limit}`); }
  
  create(offering: Partial<Offering>) {
    return this.http.post<Offering>(`${apiBase()}/provider/offerings`, offering);
  }
  
  update(id: string, updates: Partial<Offering>) {
    return this.http.patch<Offering>(`${apiBase()}/provider/offerings/${id}`, updates);
  }
  
  toggleStatus(id: string) {
    return this.http.patch<Offering>(`${apiBase()}/provider/offerings/${id}/status`, {});
  }
  
  listMyServices(page: number = 0, size: number = 10) {
    return this.http.get<PageResult<Offering>>(`${apiBase()}/provider/offerings/me?page=${page}&size=${size}`);
  }
}
