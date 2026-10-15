import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DisplayBookingsComponent } from './display-bookings';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { BookingService } from '../../core/booking.service';
import { ToastService } from '../../core/toast.service';

describe('DisplayBookings', () => {
  let component: DisplayBookingsComponent;
  let fixture: ComponentFixture<DisplayBookingsComponent>;
  let bookingService: jasmine.SpyObj<BookingService>;
  let toastService: jasmine.SpyObj<ToastService>;

  beforeEach(async () => {
    bookingService = jasmine.createSpyObj<BookingService>('BookingService', ['listMine', 'cancel']);
    bookingService.listMine.and.returnValue(of({
      content: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0
    }));
    toastService = jasmine.createSpyObj<ToastService>('ToastService', ['show', 'clear']);

    await TestBed.configureTestingModule({
      imports: [DisplayBookingsComponent],
      providers: [
        provideRouter([]),
        { provide: BookingService, useValue: bookingService },
        { provide: ToastService, useValue: toastService }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DisplayBookingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows a readable service code or abbreviated identifier', () => {
    expect(component.getServiceIdentifier({
      id: '11111111-2222-3333-4444-555555555555',
      offeringId: '11111111-2222-3333-4444-555555555555',
      offeringTitle: 'Servicio',
      serviceCode: 'SRV-001',
      price: 0,
      scheduledAt: '',
      status: 'ACTIVE',
      active: true
    })).toBe('ID: SRV-001');

    expect(component.getServiceIdentifier({
      id: '11111111-2222-3333-4444-555555555555',
      offeringId: '11111111-2222-3333-4444-555555555555',
      offeringTitle: 'Servicio',
      price: 0,
      scheduledAt: '',
      status: 'ACTIVE',
      active: true
    })).toBe('ID: #11111111');
  });

  it('uses the active badge style and informs when a reservation is already cancelled', () => {
    expect(component.getStatusClass('ACTIVE')).toBe('status-active');

    component.confirmarCancelacion({
      id: '11111111-2222-3333-4444-555555555555',
      offeringId: '11111111-2222-3333-4444-555555555555',
      offeringTitle: 'Servicio',
      price: 0,
      scheduledAt: '',
      status: 'CANCELLED',
      active: false
    });

    expect(toastService.show).toHaveBeenCalledWith(
      'Ya se ha hecho la cancelación de este recurso o de este servicio.',
      jasmine.any(Array)
    );
    expect(bookingService.cancel).not.toHaveBeenCalled();
  });
});
