import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import type { DeliveryStatus } from '../../domain/delivery-status';
import {
  NotificationLogPort,
  type FailedNotification,
  type SentNotification,
} from '../../application/ports/notification-log.port';
import { NotificationLogEntity } from './notification-log.entity';

@Injectable()
export class TypeOrmNotificationLogAdapter implements NotificationLogPort {
  constructor(
    @InjectRepository(NotificationLogEntity)
    private readonly repository: Repository<NotificationLogEntity>,
  ) {}

  async recordSent(entry: SentNotification): Promise<void> {
    await this.repository.insert({
      ...entry,
      status: 'sent',
      error: null,
      lastEventAt: new Date(),
    });
  }

  async recordFailed(entry: FailedNotification): Promise<void> {
    await this.repository.insert({
      ...entry,
      error: entry.error.slice(0, 2000),
      providerMessageId: null,
      status: 'failed',
      lastEventAt: new Date(),
    });
  }

  async findStatus(providerMessageId: string): Promise<DeliveryStatus | null> {
    const row = await this.repository.findOne({
      where: { providerMessageId },
      select: { status: true },
    });
    return row ? (row.status as DeliveryStatus) : null;
  }

  async updateDeliveryStatus(
    providerMessageId: string,
    status: DeliveryStatus,
    occurredAt: Date,
  ): Promise<void> {
    await this.repository.update(
      { providerMessageId },
      { status, lastEventAt: occurredAt },
    );
  }
}
