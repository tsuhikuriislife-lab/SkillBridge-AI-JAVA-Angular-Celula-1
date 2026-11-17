import { Injectable } from '@nestjs/common';
import type { DeliveryStatus } from '../../domain/delivery-status';
import {
  NotificationLogPort,
  type FailedNotification,
  type SentNotification,
} from '../../application/ports/notification-log.port';

export interface NotificationLogRecord {
  eventId: string;
  eventType: string;
  template: string;
  recipient: string;
  providerMessageId: string | null;
  status: DeliveryStatus;
  error: string | null;
  lastEventAt: Date;
}

/** Implementación en memoria: base del proyecto y pruebas. Leonardo la reemplaza por TypeORM. */
@Injectable()
export class InMemoryNotificationLogAdapter implements NotificationLogPort {
  readonly records: NotificationLogRecord[] = [];

  recordSent(entry: SentNotification): Promise<void> {
    this.records.push({
      ...entry,
      status: 'sent',
      error: null,
      lastEventAt: new Date(),
    });
    return Promise.resolve();
  }

  recordFailed(entry: FailedNotification): Promise<void> {
    this.records.push({
      ...entry,
      providerMessageId: null,
      status: 'failed',
      lastEventAt: new Date(),
    });
    return Promise.resolve();
  }

  findStatus(providerMessageId: string): Promise<DeliveryStatus | null> {
    const record = this.records.find(
      (r) => r.providerMessageId === providerMessageId,
    );
    return Promise.resolve(record ? record.status : null);
  }

  updateDeliveryStatus(
    providerMessageId: string,
    status: DeliveryStatus,
    occurredAt: Date,
  ): Promise<void> {
    const record = this.records.find(
      (r) => r.providerMessageId === providerMessageId,
    );
    if (record) {
      record.status = status;
      record.lastEventAt = occurredAt;
    }
    return Promise.resolve();
  }
}
