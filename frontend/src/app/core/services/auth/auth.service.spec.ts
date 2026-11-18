import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService, UserProfile } from './auth.service';

describe('AuthService (HU-14 Session Security)', () => {
  let service: AuthService;
  let httpTesting: HttpTestingController;
  let routerSpy: jasmine.SpyObj<Router>;

  const mockProfile: UserProfile = {
    id: 'user-uuid-123',
    name: 'Maria Test',
    email: 'maria@test.com',
    role: 'CUSTOMER',
    status: 'ACTIVE'
  };

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    routerSpy = jasmine.createSpyObj('Router', ['navigateByUrl']);

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: routerSpy }
      ]
    });

    service = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('no guarda ni lee JWT en localStorage al inicializar', () => {
    expect(localStorage.getItem('skillbridge_token')).toBeNull();
    expect(service.token()).toBeNull();
    expect(service.isAuthenticated()).toBeFalse();
  });

  it('restaura el perfil del usuario mediante GET /api/users/me', () => {
    service.restoreSession().subscribe(profile => {
      expect(profile).toEqual(mockProfile);
      expect(service.isAuthenticated()).toBeTrue();
      expect(service.role()).toBe('CUSTOMER');
      expect(service.user()?.name).toBe('Maria Test');
    });

    const req = httpTesting.expectOne('/api/users/me');
    expect(req.request.method).toBe('GET');
    expect(req.request.withCredentials).toBeTrue();
    req.flush(mockProfile);
  });

  it('establece estado no autenticado cuando GET /api/users/me retorna 401', () => {
    service.restoreSession().subscribe(profile => {
      expect(profile).toBeNull();
      expect(service.isAuthenticated()).toBeFalse();
      expect(service.role()).toBeNull();
    });

    const req = httpTesting.expectOne('/api/users/me');
    expect(req.request.method).toBe('GET');
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
  });

  it('inicia sesion con POST /api/auth/login y carga el perfil sin exponer JWT en localStorage', () => {
    service.login('maria@test.com', 'Pass123!').subscribe(profile => {
      expect(profile).toEqual(mockProfile);
      expect(service.isAuthenticated()).toBeTrue();
      expect(service.role()).toBe('CUSTOMER');
    });

    const loginReq = httpTesting.expectOne('/api/auth/login');
    expect(loginReq.request.method).toBe('POST');
    expect(loginReq.request.body).toEqual({ email: 'maria@test.com', password: 'Pass123!' });
    expect(loginReq.request.withCredentials).toBeTrue();
    loginReq.flush({ message: 'Inicio de sesión exitoso' });

    const profileReq = httpTesting.expectOne('/api/users/me');
    expect(profileReq.request.method).toBe('GET');
    profileReq.flush(mockProfile);

    expect(localStorage.getItem('skillbridge_token')).toBeNull();
  });

  it('cierra sesion con POST /api/auth/logout y limpia el estado local', () => {
    // Simulamos sesion activa
    (service as any).authenticated.set(true);
    (service as any).userRole.set('CUSTOMER');

    service.logout();

    const logoutReq = httpTesting.expectOne('/api/auth/logout');
    expect(logoutReq.request.method).toBe('POST');
    expect(logoutReq.request.withCredentials).toBeTrue();
    logoutReq.flush({ message: 'Sesión cerrada exitosamente' });

    expect(service.isAuthenticated()).toBeFalse();
    expect(service.role()).toBeNull();
    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/');
  });
});

