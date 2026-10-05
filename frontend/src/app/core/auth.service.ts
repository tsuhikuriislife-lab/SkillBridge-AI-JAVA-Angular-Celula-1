import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { apiBase } from './api';

interface AuthResponse { token: string; tokenType: string; }

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly key = 'skillbridge_token';
  readonly authenticated = signal(!!localStorage.getItem(this.key));

  constructor(private http: HttpClient, private router: Router) {}

  login(email: string, password: string) {
    return this.http.post<AuthResponse>(`${apiBase()}/auth/login`, { email, password })
      .pipe(tap(r => this.save(r.token)));
  }

  register(name: string, email: string, password: string) {
    return this.http.post<AuthResponse>(`${apiBase()}/auth/register`, { name, email, password })
      .pipe(tap(r => this.save(r.token)));
  }

  token(): string | null { return localStorage.getItem(this.key); }
  isAuthenticated(): boolean { return this.authenticated(); }
  isLoggedIn(): boolean {
    return this.isAuthenticated();
  }
  logout(): void { localStorage.removeItem(this.key); this.authenticated.set(false); this.router.navigateByUrl('/'); }
  private save(token: string): void { localStorage.setItem(this.key, token); this.authenticated.set(true); }
}
