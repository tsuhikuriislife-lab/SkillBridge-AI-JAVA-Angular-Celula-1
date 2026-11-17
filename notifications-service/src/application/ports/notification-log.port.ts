import type { DeliveryStatus } from '../../domain/delivery-status';

export interface SentNotification {
  eventId: string;
  eventType: string;
  template: string;
  recipient: string;
  providerMessageId: string;
}

export interface FailedNotification {
  eventId: string;
  eventType: string;
  template: string;
  recipient: string;
  error: string;
}

/** Puerto de salida: registro de auditoría de envíos (NotificationLog). */
export abstract class NotificationLogPort {
  abstract recordSent(entry: SentNotification): Promise<void>;
  abstract recordFailed(entry: FailedNotification): Promise<void>;
  abstract findStatus(
    providerMessageId: string,
  ): Promise<DeliveryStatus | null>;
  abstract updateDeliveryStatus(
    providerMessageId: string,
    status: DeliveryStatus,
    occurredAt: Date,
  ): Promise<void>;
}
