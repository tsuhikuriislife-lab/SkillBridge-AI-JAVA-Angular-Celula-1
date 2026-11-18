import { ComponentFixture, TestBed } from "@angular/core/testing";
import { provideAnimations } from "@angular/platform-browser/animations";
import { AdminDashboardComponent } from "./admin-dashboard.component";
// 👇 1. Importa los proveedores de HTTP
import { provideHttpClient } from "@angular/common/http";
import { provideHttpClientTesting } from "@angular/common/http/testing";

describe("AdminDashboardComponent", () => {
  let component: AdminDashboardComponent;
  let fixture: ComponentFixture<AdminDashboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminDashboardComponent],
      providers: [
        provideAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it("should create", () => {
    expect(component).toBeTruthy();
  });

  it('should initialize with default tab "usuarios"', () => {
    expect(component.selectedTab()).toBe("usuarios");
  });

  it("should change tab when setTab is called", () => {
    component.setTab("servicios");
    expect(component.selectedTab()).toBe("servicios");
    expect(component.currentPage()).toBe(1);
  });
});
