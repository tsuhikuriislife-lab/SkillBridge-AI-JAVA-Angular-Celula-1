import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { apiBase } from './api';

export interface UserDto {
  id: string;
  fullName?: string;
  name?: string;
  email: string;
  role?: string;
  active?: boolean;
  status?: string;
}

@Injectable({ providedIn: 'root' })
export class UserService {
  private http = inject(HttpClient);

  getAll() {
    return this.http.get<UserDto[]>(`${apiBase()}/admin/users`);
  }

  getById(id: string) {
    return this.http.get<UserDto>(`${apiBase()}/admin/users/${id}`);
  }
}