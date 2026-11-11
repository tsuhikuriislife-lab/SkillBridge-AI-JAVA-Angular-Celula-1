/** Estados de un correo en el registro de auditoría (NotificationLog). */
export const DELIVERY_STATUSES = [
  'sent',
  'delivery_delayed',
  'delivered',
  'bounced',
  'complained',
  'failed',
] as const;

export type DeliveryStatus = (typeof DELIVERY_STATUSES)[number];
