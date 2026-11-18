import { Injectable, Logger } from '@nestjs/common';
import type { DeliveryStatus } from '../domain/delivery-status';
import { canTransition } from '../domain/delivery-status-transition';
import { NotificationLogPort } from './ports/notification-log.port';

/** Forma mínima que nos interesa de un webhook de Resend. */
export interface DeliveryWebhookEvent {
  type: string;
  created_at: string;
  data?: { email_id?: string };
}

export type DeliveryOutcome = 'updated' | 'ignored' | 'not_found' | 'stale';

/** Eventos del ciclo de vida que se registran (CA 3.2). El resto (opened, clicked…) se ignora. */
const STATUS_BY_EVENT: Record<string, DeliveryStatus> = {
  'email.sent': 'sent',
  'email.delivery_delayed': 'delivery_delayed',
  'email.delivered': 'delivered',
  'email.bounced': 'bounced',
  'email.complained': 'complained',
  'email.failed': 'failed',
};

@Injectable()
export class ProcessDeliveryEventUseCase {
  private readonly logger = new Logger(ProcessDeliveryEventUseCase.name);

  constructor(private readonly notificationLog: NotificationLogPort) {}

  async execute(event: DeliveryWebhookEvent): Promise<DeliveryOutcome> {
    const next = STATUS_BY_EVENT[event.type];
    const emailId = event.data?.email_id;
    if (!next || !emailId) return 'ignored';

    const current = await this.notificationLog.findStatus(emailId);
    if (!current) {
      this.logger.warn(
        `Webhook ${event.type} de un correo que no está en el log: ${emailId}`,
      );
      return 'not_found';
    }
    if (!canTransition(current, next)) return 'stale';

    const occurredAt = new Date(event.created_at);
    await this.notificationLog.updateDeliveryStatus(
      emailId,
      next,
      Number.isNaN(occurredAt.getTime()) ? new Date() : occurredAt,
    );
    return 'updated';
  }
}
