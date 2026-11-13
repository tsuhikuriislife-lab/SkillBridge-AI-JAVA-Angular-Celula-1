import type { NotificationIntent, TemplateKey } from './notification';
import type { NotificationEvent } from './notification-event';

/**
 * Decide qué correos genera un evento (tabla en docs/notifications/CONTRACT.md §3).
 * Función pura: mismo evento → mismo resultado.
 */
export function resolveNotifications(
  event: NotificationEvent,
): NotificationIntent[] {
  const subjectEmail = event.subject.email.toLowerCase();

  const intent = (
    template: TemplateKey,
    to: string,
    name: string,
  ): NotificationIntent => ({
    template,
    to,
    recipientName: name,
    idempotencyKey: `${event.eventId}:${template}:${to.toLowerCase()}`,
  });

  const toUser = (template: TemplateKey): NotificationIntent =>
    intent(template, event.subject.email, event.subject.name);

  const toAdmins = (template: TemplateKey): NotificationIntent[] =>
    event.admins
      .filter((admin) => admin.email.toLowerCase() !== subjectEmail)
      .map((admin) => intent(template, admin.email, admin.name));

  const toProvider = (template: TemplateKey): NotificationIntent[] => {
    const email = event.data.providerEmail;
    if (!email || email.toLowerCase() === subjectEmail) return [];
    return [intent(template, email, event.data.providerName || 'Proveedor')];
  };

  const actorIsAdmin = event.actor?.role === 'ADMIN';

  switch (event.eventType) {
    case 'user.registered':
      return [toUser('welcome'), ...toAdmins('admin.new-registration')];
    case 'user.created':
      return [toUser('account-created')];
    case 'user.status.changed':
      return [
        toUser('status-changed'),
        ...(actorIsAdmin ? [] : toAdmins('admin.status-changed')),
      ];
    case 'user.role.changed':
      return [toUser('role-changed')];
    case 'user.deleted':
      return [
        toUser('account-deleted'),
        ...(actorIsAdmin ? [] : toAdmins('admin.account-deleted')),
      ];
    case 'enrollment.created':
      return [
        toUser('booking-confirmed'),
        ...toProvider('provider.new-enrollment'),
      ];
    default:
      return assertNever(event.eventType);
  }
}

function assertNever(value: never): never {
  throw new Error(`Tipo de evento no soportado: ${String(value)}`);
}
