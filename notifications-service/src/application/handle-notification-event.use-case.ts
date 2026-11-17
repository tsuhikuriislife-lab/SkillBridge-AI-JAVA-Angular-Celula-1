import { Injectable, Logger } from '@nestjs/common';
import { resolveNotifications } from '../domain/notification-policy';
import type { NotificationIntent } from '../domain/notification';
import type { NotificationEvent } from '../domain/notification-event';
import { maskEmail } from '../shared/mask-email';
import {
  EmailSenderPort,
  PermanentEmailError,
  RetryableEmailError,
} from './ports/email-sender.port';
import { IdempotencyPort } from './ports/idempotency.port';
import { NotificationLogPort } from './ports/notification-log.port';
import { TemplateRendererPort } from './ports/template-renderer.port';

export interface HandleResult {
  sent: number;
  skipped: number;
}

const toTagValue = (value: string): string =>
  value.replace(/[^a-zA-Z0-9_-]/g, '_');

@Injectable()
export class HandleNotificationEventUseCase {
  private readonly logger = new Logger(HandleNotificationEventUseCase.name);

  constructor(
    private readonly sender: EmailSenderPort,
    private readonly renderer: TemplateRendererPort,
    private readonly idempotency: IdempotencyPort,
    private readonly notificationLog: NotificationLogPort,
  ) {}

  /** Envía todos los correos del evento. Si uno falla, lanza el error (el consumidor decide reintentar). */
  async execute(event: NotificationEvent): Promise<HandleResult> {
    const result: HandleResult = { sent: 0, skipped: 0 };

    for (const intent of resolveNotifications(event)) {
      const claim = await this.idempotency.claim(intent.idempotencyKey);
      if (claim === 'already_sent') {
        result.skipped++;
        continue;
      }
      if (claim === 'in_progress') {
        throw new RetryableEmailError(
          `Envío en curso en otro proceso: ${intent.idempotencyKey}`,
        );
      }

      try {
        await this.sendOne(event, intent);
        await this.idempotency.markSent(intent.idempotencyKey);
        result.sent++;
      } catch (error) {
        await this.idempotency.release(intent.idempotencyKey);
        if (error instanceof PermanentEmailError) {
          await this.safeLog(() =>
            this.notificationLog.recordFailed({
              eventId: event.eventId,
              eventType: event.eventType,
              template: intent.template,
              recipient: intent.to,
              error: error.message,
            }),
          );
        }
        throw error;
      }
    }

    return result;
  }

  private async sendOne(
    event: NotificationEvent,
    intent: NotificationIntent,
  ): Promise<void> {
    const rendered = this.renderer.render(
      intent.template,
      event,
      intent.recipientName,
    );
    const sent = await this.sender.send({
      ...rendered,
      to: intent.to,
      idempotencyKey: intent.idempotencyKey,
      tags: [
        { name: 'event_type', value: toTagValue(event.eventType) },
        { name: 'template', value: toTagValue(intent.template) },
      ],
    });
    this.logger.log(
      `eventId=${event.eventId} template=${intent.template} to=${maskEmail(intent.to)} providerId=${sent.id}`,
    );
    await this.safeLog(() =>
      this.notificationLog.recordSent({
        eventId: event.eventId,
        eventType: event.eventType,
        template: intent.template,
        recipient: intent.to,
        providerMessageId: sent.id,
      }),
    );
  }

  /** El registro de auditoría nunca debe provocar un reenvío del correo: sus errores sólo se loguean. */
  private async safeLog(write: () => Promise<void>): Promise<void> {
    try {
      await write();
    } catch (error) {
      this.logger.warn(
        `No se pudo escribir en NotificationLog: ${error instanceof Error ? error.message : String(error)}`,
      );
    }
  }
}
