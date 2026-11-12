import { loadFixture, type FixtureName } from '../test-support/load-fixture';
import type { NotificationEvent } from './notification-event';
import { resolveNotifications } from './notification-policy';

const fixture = (name: FixtureName): NotificationEvent =>
  loadFixture(name) as NotificationEvent;

describe('resolveNotifications', () => {
  it.each([
    ['user.registered', ['welcome', 'admin.new-registration']],
    ['user.created', ['account-created']],
    ['user.status.changed', ['status-changed', 'admin.status-changed']],
    ['user.role.changed', ['role-changed']],
    ['user.deleted', ['account-deleted', 'admin.account-deleted']],
    ['enrollment.created', ['booking-confirmed', 'provider.new-enrollment']],
  ] as const)('%s genera las plantillas correctas', (name, expected) => {
    expect(resolveNotifications(fixture(name)).map((n) => n.template)).toEqual(
      expected,
    );
  });

  it('no avisa a los admins si el cambio de estado lo hizo un admin', () => {
    const event = fixture('user.status.changed');
    event.actor = { id: null, email: 'admin@test.com', role: 'ADMIN' };
    expect(resolveNotifications(event).map((n) => n.template)).toEqual([
      'status-changed',
    ]);
  });

  it('no envía a un admin el aviso sobre sí mismo', () => {
    const event = fixture('user.registered');
    event.admins = [{ email: event.subject.email.toUpperCase(), name: 'Yo' }];
    expect(resolveNotifications(event)).toHaveLength(1);
  });

  it('no avisa al proveedor si no viene su correo', () => {
    const event = fixture('enrollment.created');
    delete event.data.providerEmail;
    expect(resolveNotifications(event).map((n) => n.template)).toEqual([
      'booking-confirmed',
    ]);
  });

  it('genera llaves de idempotencia únicas por correo', () => {
    const keys = resolveNotifications(fixture('user.registered')).map(
      (n) => n.idempotencyKey,
    );
    expect(new Set(keys).size).toBe(keys.length);
  });
});
