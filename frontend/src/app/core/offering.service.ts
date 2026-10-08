import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { apiBase } from './api';

export interface Offering {
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
}

@Injectable({ providedIn: 'root' })
export class OfferingService {
  constructor(private http: HttpClient) {}
  list() { return this.http.get<Offering[]>(`${apiBase()}/offerings`); }
}