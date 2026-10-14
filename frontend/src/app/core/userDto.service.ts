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

  create(user: { name: string; email: string; password?: string; role: string }) {
    return this.http.post<UserDto>(`${apiBase()}/admin/users`, user);
  }

  update(id: string, updates: Partial<UserDto>) {
    return this.http.patch<UserDto>(`${apiBase()}/admin/users/${id}`, updates);
  }

  delete(id: string) {
    return this.http.delete<void>(`${apiBase()}/admin/users/${id}`);
  }
}