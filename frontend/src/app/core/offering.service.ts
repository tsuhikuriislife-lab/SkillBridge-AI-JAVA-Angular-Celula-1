import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { apiBase } from './api';
import { Observable, forkJoin, of } from 'rxjs';
import { map, switchMap, shareReplay } from 'rxjs/operators';

export interface Offering {
  id: string;
  title: string;
  description: string;
  category: string;
  categoryId?: string;
  price: number;
  active: boolean;
  startDate?: string;
  startDay?: string;
  sessionDuration?: number;
  frequency?: string;
  numberOfSessions?: number;
  photoUrl?: string;
  createdBy?: string;
  capacity?: number;
  code?: string;
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
      createdBy: backendObj.createdBy,
      capacity: backendObj.capacity || 10,
      code: backendObj.code
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
  
  create(offering: any): Observable<Offering> {
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
          switchMap(createdService => {
            const days: string[] = offering.startDays && offering.startDays.length > 0 ? offering.startDays : [offering.startDay];
            const scheduleRequests = days.map(day => {
              const scheduleReq = {
                startDay: day,
                sessionDuration: offering.sessionDuration,
                frequency: offering.frequency,
                numberOfSessions: offering.numberOfSessions,
                startDate: this.getNextDateForDay(offering.startDate, day),
                startTime: offering.startTime.length === 5 ? offering.startTime + ':00' : offering.startTime
              };
              return this.http.post<any>(`${apiBase()}/provider/services/${createdService.id}/schedule`, scheduleReq);
            });
            
            if (scheduleRequests.length === 0) {
              return of(this.mapToFrontend(createdService, catMap));
            }
            return forkJoin(scheduleRequests).pipe(
              map(() => this.mapToFrontend(createdService, catMap))
            );
          })
        )
      )
    );
  }

  private getNextDateForDay(baseDateStr: string, targetDay: string): string {
    const daysMap: any = { 'SUNDAY': 0, 'MONDAY': 1, 'TUESDAY': 2, 'WEDNESDAY': 3, 'THURSDAY': 4, 'FRIDAY': 5, 'SATURDAY': 6 };
    const targetIdx = daysMap[targetDay];
    
    const parts = baseDateStr.split('-');
    const year = parseInt(parts[0], 10);
    const month = parseInt(parts[1], 10);
    const day = parseInt(parts[2], 10);
    const date = new Date(year, month - 1, day);
    
    let currentIdx = date.getDay();
    let daysToAdd = targetIdx - currentIdx;
    if (daysToAdd < 0) {
      daysToAdd += 7;
    }
    
    date.setDate(date.getDate() + daysToAdd);
    
    const yyyy = date.getFullYear();
    const mm = String(date.getMonth() + 1).padStart(2, '0');
    const dd = String(date.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
  }
  
  update(id: string, updates: Partial<Offering>, currentStatus?: boolean): Observable<Offering> {
    const req = {
      name: updates.title,
      categoryId: updates.categoryId || updates.category,
      price: updates.price,
      detail: updates.description,
      shortDescription: updates.description,
      capacity: updates.capacity || 10,
      code: updates.code || "SRV-" + id.substring(0, 4),
      status: (updates.active !== undefined ? updates.active : currentStatus) ? "ACTIVE" : "INACTIVE"
    };
    return this.resolveCategories().pipe(
      switchMap(catMap => 
        this.http.put<any>(`${apiBase()}/provider/services/${id}`, req).pipe(
          map(o => this.mapToFrontend(o, catMap))
        )
      )
    );
  }
  
  toggleStatus(id: string, currentStatus: boolean): Observable<Offering> {
    const newStatus = currentStatus ? "INACTIVE" : "ACTIVE";
    return this.resolveCategories().pipe(
      switchMap(catMap => 
        this.http.patch<any>(`${apiBase()}/provider/services/${id}/status`, { status: newStatus }).pipe(
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

  getSchedules(serviceId: string): Observable<any[]> {
    return this.http.get<any[]>(`${apiBase()}/provider/services/${serviceId}/schedule`);
  }

  deleteSchedule(serviceId: string, scheduleId: string): Observable<any> {
    return this.http.delete(`${apiBase()}/provider/services/${serviceId}/schedule/${scheduleId}`);
  }
}
