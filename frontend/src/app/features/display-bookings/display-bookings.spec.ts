import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DisplayBookingsComponent } from './display-bookings';

import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

describe('DisplayBookings', () => {
  let component: DisplayBookingsComponent;
  let fixture: ComponentFixture<DisplayBookingsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DisplayBookingsComponent],
      providers: [provideHttpClient(), provideRouter([])]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DisplayBookingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
