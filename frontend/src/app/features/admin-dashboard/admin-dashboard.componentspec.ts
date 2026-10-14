import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { AdminDashboardComponent } from './admin-dashboard.component';

describe('AdminDashboardComponent', () => {
  let component: AdminDashboardComponent;
  let fixture: ComponentFixture<AdminDashboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminDashboardComponent],
      providers: [
        provideAnimations() // Provee soporte de animaciones si el template o navegador lo requiere
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AdminDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize with default tab "usuarios"', () => {
    expect(component.selectedTab()).toBe('usuarios');
  });

  it('should change tab when setTab is called', () => {
    component.setTab('proveedores');
    expect(component.selectedTab()).toBe('proveedores');
    expect(component.currentPage()).toBe(1);
  });
});