import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, map, of, switchMap, tap } from 'rxjs';
import { apiBase } from './api';

interface AuthResponse { token: string; tokenType: string; }

export interface CurrentUser { name: string; email: string; role: string; }

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly key = 'skillbridge_token';
  readonly authenticated = signal(!!localStorage.getItem(this.key));
  readonly currentUser = signal<CurrentUser | null>(null);
  readonly isProvider = computed(() => this.currentUser()?.role === 'PROVIDER');

  constructor(private http: HttpClient, private router: Router) {
    if (this.authenticated()) {
      this.loadProfile().subscribe();
    }
  }

  login(email: string, password: string) {
    return this.http.post<AuthResponse>(`${apiBase()}/auth/login`, { email, password })
      .pipe(
        tap(r => this.save(r.token)),
        switchMap(r => this.loadProfile().pipe(map(() => r)))
      );
  }

  register(name: string, email: string, password: string) {
    return this.http.post<AuthResponse>(`${apiBase()}/auth/register`, { name, email, password })
      .pipe(
        tap(r => this.save(r.token)),
        switchMap(r => this.loadProfile().pipe(map(() => r)))
      );
  }

  /** Pide al backend quién es el usuario y su rol actual (leído de la base de datos). */
  loadProfile(): Observable<CurrentUser | null> {
    return this.http.get<CurrentUser>(`${apiBase()}/users/me`).pipe(
      tap(user => this.currentUser.set(user)),
      catchError(() => {
        this.currentUser.set(null);
        return of(null);
      })
    );
  }

  token(): string | null { return localStorage.getItem(this.key); }
  isAuthenticated(): boolean { return this.authenticated(); }
  isLoggedIn(): boolean {
    return this.isAuthenticated();
  }
  logout(): void {
    localStorage.removeItem(this.key);
    this.authenticated.set(false);
    this.currentUser.set(null);
    this.router.navigateByUrl('/');
  }
  private save(token: string): void { localStorage.setItem(this.key, token); this.authenticated.set(true); }
}