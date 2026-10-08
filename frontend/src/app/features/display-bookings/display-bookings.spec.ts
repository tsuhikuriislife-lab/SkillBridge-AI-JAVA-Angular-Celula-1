import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DisplayBookings } from './display-bookings';

describe('DisplayBookings', () => {
  let component: DisplayBookings;
  let fixture: ComponentFixture<DisplayBookings>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DisplayBookings]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DisplayBookings);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
