/** Contrato v1 — docs/notifications/CONTRACT.md */
export const NOTIFICATION_EVENT_TYPES = [
  'user.registered',
  'user.created',
  'user.status.changed',
  'user.role.changed',
  'user.deleted',
  'enrollment.created',
] as const;

export type NotificationEventType = (typeof NOTIFICATION_EVENT_TYPES)[number];
export type Role = 'CUSTOMER' | 'PROVIDER' | 'ADMIN';
export type AccountStatus = 'ACTIVE' | 'INACTIVE';

export interface EventActor {
  id: string | null;
  email: string;
  role: Role | null;
}

export interface EventSubject {
  id: string;
  name: string;
  email: string;
  role: Role;
  status: AccountStatus;
}

export interface AdminRecipient {
  email: string;
  name: string;
}

export interface NotificationEvent {
  eventId: string;
  eventType: NotificationEventType;
  version: number;
  occurredAt: string;
  actor: EventActor | null;
  subject: EventSubject;
  admins: AdminRecipient[];
  data: Record<string, string>;
}
