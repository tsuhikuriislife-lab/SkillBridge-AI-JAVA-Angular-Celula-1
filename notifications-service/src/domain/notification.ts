export const TEMPLATE_KEYS = [
  'welcome',
  'admin.new-registration',
  'account-created',
  'status-changed',
  'admin.status-changed',
  'role-changed',
  'account-deleted',
  'admin.account-deleted',
  'booking-confirmed',
  'provider.new-enrollment',
] as const;

export type TemplateKey = (typeof TEMPLATE_KEYS)[number];

/** "Hay que mandar ESTA plantilla a ESTA persona" (todavía sin HTML). */
export interface NotificationIntent {
  template: TemplateKey;
  to: string;
  recipientName: string;
  idempotencyKey: string;
}

export interface RenderedEmail {
  subject: string;
  html: string;
  text: string;
}

export interface EmailTag {
  name: string;
  value: string;
}

export interface OutgoingEmail extends RenderedEmail {
  to: string;
  idempotencyKey: string;
  tags: EmailTag[];
}
