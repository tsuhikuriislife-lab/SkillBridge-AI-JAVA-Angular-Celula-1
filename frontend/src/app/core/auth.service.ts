import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, catchError, of, switchMap } from 'rxjs';
import { apiBase } from './api';

export interface UserProfile {
  id: string;
  name: string;
  email: string;
  role: string;
  image?: string | null;
  gender?: string | null;
  birthDate?: string | null;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AuthMessageResponse {
  message: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly authenticated = signal<boolean>(false);
  readonly userRole = signal<string | null>(null);
  readonly currentUser = signal<UserProfile | null>(null);
  readonly sessionExpired = signal<boolean>(false);
  readonly initialized = signal<boolean>(false);

  constructor(private http: HttpClient, private router: Router) {}

  restoreSession(): Observable<UserProfile | null> {
    return this.http.get<UserProfile>(`${apiBase()}/users/me`, { withCredentials: true }).pipe(
      tap(profile => {
        this.currentUser.set(profile);
        this.authenticated.set(true);
        this.userRole.set(profile.role);
        this.sessionExpired.set(false);
        this.initialized.set(true);
      }),
      catchError(() => {
        this.clearLocalState();
        this.initialized.set(true);
        return of(null);
      })
    );
  }

  login(email: string, password: string): Observable<UserProfile> {
    return this.http.post<AuthMessageResponse>(
      `${apiBase()}/auth/login`,
      { email, password },
      { withCredentials: true }
    ).pipe(
      switchMap(() => this.fetchProfile())
    );
  }

  register(name: string, email: string, password: string): Observable<UserProfile> {
    return this.http.post<AuthMessageResponse>(
      `${apiBase()}/auth/register`,
      { name, email, password },
      { withCredentials: true }
    ).pipe(
      switchMap(() => this.fetchProfile())
    );
  }

  private fetchProfile(): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${apiBase()}/users/me`, { withCredentials: true }).pipe(
      tap(profile => {
        this.currentUser.set(profile);
        this.authenticated.set(true);
        this.userRole.set(profile.role);
        this.sessionExpired.set(false);
      })
    );
  }

  /**
   * El token no se almacena en el frontend; se gestiona exclusivamente
   * en el navegador mediante cookies HttpOnly por motivos de seguridad (HU-14).
   */
  token(): string | null {
    return null;
  }

  isAuthenticated(): boolean {
    return this.authenticated();
  }

  isLoggedIn(): boolean {
    return this.isAuthenticated();
  }

  role(): string | null {
    return this.userRole();
  }

  user(): UserProfile | null {
    return this.currentUser();
  }

  logout(): void {
    this.http.post<AuthMessageResponse>(`${apiBase()}/auth/logout`, {}, { withCredentials: true })
      .pipe(catchError(() => of(null)))
      .subscribe(() => {
        this.clearLocalState();
        this.router.navigateByUrl('/');
      });
  }

  triggerSessionExpired(): void {
    this.sessionExpired.set(true);
  }

  forceLogout(): void {
    this.http.post<AuthMessageResponse>(`${apiBase()}/auth/logout`, {}, { withCredentials: true })
      .pipe(catchError(() => of(null)))
      .subscribe(() => {
        this.clearLocalState();
        this.sessionExpired.set(false);
        this.router.navigateByUrl('/login');
      });
  }

  private clearLocalState(): void {
    this.authenticated.set(false);
    this.userRole.set(null);
    this.currentUser.set(null);
  }
}
