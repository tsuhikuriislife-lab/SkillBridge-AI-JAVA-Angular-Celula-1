import { TestBed } from '@angular/core/testing';
import { AuthService } from '../core/auth.service';
import { NotificationService, NotificationSummary } from '../core/notification.service';
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

    it('shows translated enrollment labels and messages for known statuses', () => {
        const fixture = TestBed.createComponent(MyNotificationsComponent);
        const component = fixture.componentInstance;
        const statusCases: Array<[string, string, string, string]> = [
            ['ACTIVE', 'Activa', 'badge-active', 'Tu inscripción está confirmada. Ya puedes acceder al servicio y a sus sesiones.'],
            ['CANCELLED', 'Cancelada', 'badge-cancelled', 'Tu inscripción fue cancelada. Si fue un error, puedes volver a reservar este servicio.'],
            ['PENDING', 'Pendiente', 'badge-pending', 'Estamos esperando la confirmación de tu inscripción. Te avisaremos cuando cambie.'],
            ['COMPLETED', 'Completada', 'badge-completed', 'Tu inscripción fue completada. ¡Gracias por participar!']
        ];

        for (const [status, label, badgeClass, message] of statusCases) {
            const notification: NotificationSummary = {
                id: status,
                type: 'ENROLLMENT_STATUS_CHANGED',
                message: `El estado de tu inscripción es ahora: ${status}`,
                status: 'PROCESSED',
                createdAt: '',
                enrollmentStatus: status
            };

            expect(component.getStatusLabel(notification)).toBe(label);
            expect(component.getStatusClass(notification)).toBe(badgeClass);
            expect(component.getStatusMessage(notification)).toBe(message);
        }
    });

    it('preserves unknown status values and the previous active-enrollment summary fallback', () => {
        const fixture = TestBed.createComponent(MyNotificationsComponent);
        const component = fixture.componentInstance;
        const unknownStatus: NotificationSummary = {
            id: 'unknown-status',
            type: 'ENROLLMENT_STATUS_CHANGED',
            message: 'El estado de tu inscripción es ahora: on_hold',
            status: 'PROCESSED',
            createdAt: ''
        };
        const notificationWithoutStatus: NotificationSummary = {
            id: 'missing-status',
            type: 'ENROLLMENT_STATUS_CHANGED',
            message: 'Reserva confirmada',
            status: 'PROCESSED',
            createdAt: ''
        };

        expect(component.getStatusLabel(unknownStatus)).toBe('on_hold');
        expect(component.getStatusClass(unknownStatus)).toBe('badge-default');
        expect(component.getStatusMessage(unknownStatus)).toBe('El estado de tu inscripción es ahora: on_hold');

        component.notifications = [notificationWithoutStatus];
        expect(component.countActiveEnrollments).toBe(1);
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
