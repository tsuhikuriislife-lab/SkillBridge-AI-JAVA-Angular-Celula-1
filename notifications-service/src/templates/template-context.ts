import type { NotificationEvent } from '../domain/notification-event';

const ROLE_LABELS: Record<string, string> = {
  CUSTOMER: 'Cliente',
  PROVIDER: 'Proveedor',
  ADMIN: 'Administrador',
};

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Activa',
  INACTIVE: 'Inactiva',
};

const ROLE_HINTS: Record<string, string> = {
  PROVIDER:
    'Ahora puedes publicar y gestionar tus propios servicios desde el panel de proveedor.',
  CUSTOMER: 'Puedes explorar los servicios disponibles e inscribirte en ellos.',
  ADMIN: 'Ahora tienes acceso al panel de administración de la plataforma.',
};

const DATE_TIME = new Intl.DateTimeFormat('es-CO', {
  dateStyle: 'long',
  timeStyle: 'short',
  timeZone: 'America/Bogota',
});

/** Las fechas sin hora (2026-11-20) se formatean en UTC para que no "retrocedan" un día. */
const DATE_ONLY = new Intl.DateTimeFormat('es-CO', {
  dateStyle: 'long',
  timeZone: 'UTC',
});

const labelOf = (map: Record<string, string>, key?: string): string =>
  key ? (map[key] ?? key) : '—';

const firstName = (fullName: string): string =>
  fullName.trim().split(/\s+/)[0] || fullName;

function formatDateTime(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : DATE_TIME.format(date);
}

function formatDay(isoDay?: string): string {
  if (!isoDay) return '—';
  const date = new Date(`${isoDay}T00:00:00Z`);
  return Number.isNaN(date.getTime()) ? isoDay : DATE_ONLY.format(date);
}

export interface DetailRow {
  label: string;
  value: string;
}

/**
 * Convierte el evento en las variables que usan las plantillas .hbs.
 * Un solo contexto "plano" para todas las plantillas: cada .hbs usa sólo las que necesita.
 */
export function buildTemplateContext(
  event: NotificationEvent,
  recipientName: string,
  frontendUrl: string,
): Record<string, unknown> {
  const { subject, data, actor } = event;
  const isSelf =
    !!actor && actor.email.toLowerCase() === subject.email.toLowerCase();
  const actorLabel = !actor
    ? 'Tú (registro)'
    : isSelf
      ? 'Tú'
      : actor.role === 'ADMIN'
        ? 'Un administrador'
        : actor.email;
  const occurredAtLabel = formatDateTime(event.occurredAt);

  const subjectDetails: DetailRow[] = [
    { label: 'Nombre', value: subject.name },
    { label: 'Correo', value: subject.email },
    { label: 'Rol', value: labelOf(ROLE_LABELS, subject.role) },
    { label: 'Fecha', value: occurredAtLabel },
  ];

  const bookingDetails: DetailRow[] = [
    { label: 'Servicio', value: data.serviceName ?? '—' },
    { label: 'Inicio', value: formatDay(data.startDate) },
    { label: 'Hora', value: data.startTime || '—' },
    { label: 'Sesiones', value: data.sessions ?? '—' },
    { label: 'Finaliza', value: formatDay(data.endDate) },
    { label: 'Proveedor', value: data.providerName || '—' },
  ];

  return {
    // Personas
    firstName: firstName(subject.name),
    subjectName: subject.name,
    subjectEmail: subject.email,
    recipientFirstName: firstName(recipientName),
    actorLabel,
    actorLabelLower: actorLabel.toLowerCase(),
    changedByAdmin: actor?.role === 'ADMIN' && !isSelf,
    // Rol / estado
    roleLabel: labelOf(ROLE_LABELS, subject.role),
    previousRoleLabel: labelOf(ROLE_LABELS, data.previousRole),
    newRoleLabel: labelOf(ROLE_LABELS, data.newRole),
    roleHint: ROLE_HINTS[data.newRole ?? ''] ?? '',
    previousStatusLabel: labelOf(STATUS_LABELS, data.previousStatus),
    newStatusLabel: labelOf(STATUS_LABELS, data.newStatus),
    deactivated: data.newStatus === 'INACTIVE',
    // Reserva
    serviceName: data.serviceName ?? '',
    startDateLabel: formatDay(data.startDate),
    // Tablas
    subjectDetails,
    bookingDetails,
    changeDetails: [
      { label: 'Realizado por', value: actorLabel },
      { label: 'Fecha', value: occurredAtLabel },
    ] satisfies DetailRow[],
    statusDetails: [
      ...subjectDetails,
      {
        label: 'Estado anterior',
        value: labelOf(STATUS_LABELS, data.previousStatus),
      },
      { label: 'Estado nuevo', value: labelOf(STATUS_LABELS, data.newStatus) },
    ] satisfies DetailRow[],
    // Enlaces (rutas reales de frontend/src/app/app.routes.ts)
    frontendUrl,
    loginUrl: `${frontendUrl}/login`,
    adminUrl: `${frontendUrl}/admin`,
    bookingsUrl: `${frontendUrl}/bookings`,
    providerServicesUrl: `${frontendUrl}/provider/services`,
    roleUrl:
      data.newRole === 'PROVIDER'
        ? `${frontendUrl}/provider`
        : data.newRole === 'ADMIN'
          ? `${frontendUrl}/admin`
          : frontendUrl,
  };
}
