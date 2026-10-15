import { TestBed } from '@angular/core/testing';
import { AuthService } from '../core/auth.service';
import { NotificationService } from '../core/notification.service';
import { MyNotificationsComponent } from './my-notifications.component';

describe('MyNotificationsComponent', () => {
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MyNotificationsComponent],
            providers: [
                { provide: AuthService, useValue: { role: () => 'CUSTOMER' } },
                { provide: NotificationService, useValue: { listMine: () => { throw new Error('Unexpected notification request'); } } }
            ]
        });
    });

    it('formats the enrollment status and reservation UUID on separate lines', () => {
        const fixture = TestBed.createComponent(MyNotificationsComponent);
        const message = fixture.componentInstance.formatMessage(
            'El estado de tu inscripción es ahora: CANCELLED Reserva: 11111111-1111-1111-1111-111111111111'
        );

        expect(message).toBe(
            'El estado de tu inscripción es ahora: Cancelada\n\nReserva: 11111111-1111-1111-1111-111111111111'
        );
    });

    it('translates status codes in other notification messages', () => {
        const fixture = TestBed.createComponent(MyNotificationsComponent);

        expect(fixture.componentInstance.formatMessage('Estado actualizado a ACTIVE')).toBe('Estado actualizado a Activa');
    });

    it('shows the requested labels for user creation and offering status activities', () => {
        const fixture = TestBed.createComponent(MyNotificationsComponent);
        const component = fixture.componentInstance;
        const userCreated = {
            id: 'user-activity', type: 'ADMIN_ACTIVITY', message: '', status: 'PROCESSED',
            createdAt: '', title: 'USUARIO CREADO'
        };
        const offeringStatusChanged = {
            id: 'offering-activity', type: 'ADMIN_ACTIVITY', message: '', status: 'PROCESSED',
            createdAt: '', title: 'OFFERING_STATUS_CHANGED'
        };

        expect(component.getActionTitle(userCreated)).toBe('Usuario creado');
        expect(component.getActionTag(userCreated)).toBe('Nueva cuenta');
        expect(component.getActionTitle(offeringStatusChanged)).toBe('Oferta modificada');
        expect(component.getActionTag(offeringStatusChanged)).toBe('Cambio de estado');
    });
});
