import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { apiBase } from './api';
import { Observable } from 'rxjs';
import { map, switchMap, shareReplay } from 'rxjs/operators';

export interface Offering {
  id: string;
  title: string;
  description: string;
  category: string;
  categoryId?: string; // Add categoryId
  price: number;
  active: boolean;
  startTime?: string;
  endTime?: string;
  endDay?: string;
  photoUrl?: string;
  createdBy?: string;
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
  private categoriesCache$: Observable<any[]> | null = null;

  constructor(private http: HttpClient) {}

  getCategories(): Observable<any[]> {
    if (!this.categoriesCache$) {
      this.categoriesCache$ = this.http.get<any[]>(`${apiBase()}/catalog?type=CATEGORY`).pipe(
        shareReplay(1)
      );
    }
    return this.categoriesCache$;
  }

  private resolveCategories(): Observable<Map<string, string>> {
    return this.getCategories().pipe(
      map(categories => {
        const catMap = new Map<string, string>();
        categories.forEach(c => catMap.set(c.id, c.name));
        return catMap;
      })
    );
  }

  private mapToFrontend(backendObj: any, catMap: Map<string, string>): Offering {
    return {
      id: backendObj.id,
      title: backendObj.name,
      description: backendObj.shortDescription || backendObj.detail,
      category: catMap.get(backendObj.categoryId) || backendObj.categoryId, 
      categoryId: backendObj.categoryId,
      price: backendObj.price,
      active: backendObj.status === 'ACTIVE',
      photoUrl: 'images/placeholder.jpg',
      createdBy: backendObj.createdBy
    };
  }

  list(): Observable<Offering[]> {
    return this.resolveCategories().pipe(
      switchMap(catMap => 
        this.http.get<PageResult<any>>(`${apiBase()}/services`).pipe(
          map(page => page.content.map(o => this.mapToFrontend(o, catMap)))
        )
      )
    );
  }
  
  getById(id: string): Observable<Offering> {
    return this.resolveCategories().pipe(
      switchMap(catMap => 
        this.http.get<any>(`${apiBase()}/services/${id}`).pipe(
          map(o => this.mapToFrontend(o, catMap))
        )
      )
    );
  }
  
  getTopCategories(limit: number = 8): Observable<CategoryCount[]> {
    return this.getCategories().pipe(
      map(cats => cats.slice(0, limit).map(c => ({ name: c.name, count: 10 })))
    );
  }
  
  create(offering: Partial<Offering>): Observable<Offering> {
    const req = {
      name: offering.title,
      categoryId: offering.categoryId || offering.category,
      price: offering.price,
      detail: offering.description,
      shortDescription: offering.description,
      capacity: 10,
      code: "SRV-" + Math.floor(Math.random() * 10000),
      status: "ACTIVE"
    };
    return this.resolveCategories().pipe(
      switchMap(catMap => 
        this.http.post<any>(`${apiBase()}/provider/services`, req).pipe(
          map(o => this.mapToFrontend(o, catMap))
        )
      )
    );
  }
  
  update(id: string, updates: Partial<Offering>): Observable<Offering> {
    const req = {
      name: updates.title,
      categoryId: updates.categoryId || updates.category,
      price: updates.price,
      detail: updates.description,
      shortDescription: updates.description,
      capacity: 10,
      code: "SRV-" + id.substring(0, 4),
      status: updates.active ? "ACTIVE" : "INACTIVE"
    };
    return this.resolveCategories().pipe(
      switchMap(catMap => 
        this.http.put<any>(`${apiBase()}/provider/services/${id}`, req).pipe(
          map(o => this.mapToFrontend(o, catMap))
        )
      )
    );
  }
  
  toggleStatus(id: string): Observable<Offering> {
    return this.resolveCategories().pipe(
      switchMap(catMap => 
        this.http.patch<any>(`${apiBase()}/provider/services/${id}/status`, { status: "INACTIVE" }).pipe(
          map(o => this.mapToFrontend(o, catMap))
        )
      )
    );
  }
  
  listMyServices(page: number = 0, size: number = 10): Observable<PageResult<Offering>> {
    return this.resolveCategories().pipe(
      switchMap(catMap => 
        this.http.get<any[]>(`${apiBase()}/provider/services/me`).pipe(
          map(list => ({
            content: list.map(o => this.mapToFrontend(o, catMap)),
            totalPages: 1,
            totalElements: list.length,
            number: 0
          }))
        )
      )
    );
  }
}
