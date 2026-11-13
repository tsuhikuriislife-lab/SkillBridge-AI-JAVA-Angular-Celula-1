import { AmqpConnection, RabbitSubscribe } from '@golevelup/nestjs-rabbitmq';
import { Injectable, Logger } from '@nestjs/common';
import type { ConsumeMessage } from 'amqplib';
import { HandleNotificationEventUseCase } from '../../application/handle-notification-event.use-case';
import { PermanentEmailError } from '../../application/ports/email-sender.port';
import type { NotificationEvent } from '../../domain/notification-event';
import { MetricsService } from './metrics/metrics.service';
import { parseNotificationEvent } from './notification-event.dto';
import {
  DEAD_LETTER_QUEUE,
  EVENTS_EXCHANGE,
  EVENTS_QUEUE,
  MAX_RETRIES,
  RETRY_COUNT_HEADER,
  RETRY_QUEUES,
  ROUTING_KEYS,
} from './rabbit.constants';

type FailureReason =
  'invalid_payload' | 'permanent_error' | 'max_retries_exceeded';

const errorMessage = (error: unknown): string =>
  error instanceof Error ? error.message : String(error);

@Injectable()
export class NotificationEventsConsumer {
  private readonly logger = new Logger(NotificationEventsConsumer.name);

  constructor(
    private readonly handleEvent: HandleNotificationEventUseCase,
    private readonly amqp: AmqpConnection,
    private readonly metrics: MetricsService,
  ) {}

  @RabbitSubscribe({
    exchange: EVENTS_EXCHANGE,
    routingKey: ROUTING_KEYS,
    queue: EVENTS_QUEUE,
    queueOptions: {
      durable: true,
      arguments: {
        'x-dead-letter-exchange': '',
        'x-dead-letter-routing-key': DEAD_LETTER_QUEUE,
      },
    },
  })
  async onEvent(payload: unknown, amqpMsg: ConsumeMessage): Promise<void> {
    const stopTimer = this.metrics.startProcessingTimer();
    const retries = this.retryCountOf(amqpMsg);

    try {
      let event: NotificationEvent;
      try {
        event = await parseNotificationEvent(payload);
      } catch (error) {
        this.metrics.failedAttempt('invalid_payload');
        this.logger.error(`Mensaje inválido → DLQ: ${errorMessage(error)}`);
        await this.sendToDlq(
          payload,
          amqpMsg,
          'invalid_payload',
          errorMessage(error),
        );
        return;
      }

      try {
        const result = await this.handleEvent.execute(event);
        if (result.sent === 0 && result.skipped > 0) {
          this.metrics.duplicate(event.eventType);
          this.logger.warn(
            `Re-entrega ignorada eventId=${event.eventId} (ya enviado)`,
          );
          return;
        }
        this.metrics.processed(event.eventType);
        this.logger.log(
          `Procesado eventId=${event.eventId} type=${event.eventType} enviados=${result.sent} omitidos=${result.skipped} reintentos=${retries}`,
        );
      } catch (error) {
        this.metrics.failedAttempt(event.eventType);
        const permanent = error instanceof PermanentEmailError;

        if (!permanent && retries < MAX_RETRIES) {
          const target = RETRY_QUEUES[retries];
          this.logger.warn(
            `Fallo transitorio eventId=${event.eventId} → ${target.name} (reintento ${retries + 1}/${MAX_RETRIES}): ${errorMessage(error)}`,
          );
          await this.sendToRetry(payload, amqpMsg, retries + 1, target.name);
        } else {
          const reason: FailureReason = permanent
            ? 'permanent_error'
            : 'max_retries_exceeded';
          this.logger.error(
            `eventId=${event.eventId} → DLQ (${reason}): ${errorMessage(error)}`,
          );
          await this.sendToDlq(payload, amqpMsg, reason, errorMessage(error));
        }
      }
    } finally {
      stopTimer();
    }
  }

  private retryCountOf(msg: ConsumeMessage): number {
    const value: unknown = msg.properties.headers?.[RETRY_COUNT_HEADER];
    const count = Number(value ?? 0);
    return Number.isFinite(count) && count > 0 ? count : 0;
  }

  private baseHeaders(msg: ConsumeMessage): Record<string, unknown> {
    const headers: Record<string, unknown> = {
      ...(msg.properties.headers ?? {}),
    };
    delete headers['x-death']; // lo agrega RabbitMQ al hacer dead-letter; no se reenvía
    return headers;
  }

  private async sendToRetry(
    payload: unknown,
    msg: ConsumeMessage,
    retryCount: number,
    queue: string,
  ): Promise<void> {
    await this.amqp.publish('', queue, payload, {
      persistent: true,
      messageId: msg.properties.messageId as string | undefined,
      headers: { ...this.baseHeaders(msg), [RETRY_COUNT_HEADER]: retryCount },
    });
  }

  private async sendToDlq(
    payload: unknown,
    msg: ConsumeMessage,
    reason: FailureReason,
    detail: string,
  ): Promise<void> {
    await this.amqp.publish('', DEAD_LETTER_QUEUE, payload, {
      persistent: true,
      messageId: msg.properties.messageId as string | undefined,
      headers: {
        ...this.baseHeaders(msg),
        'x-failure-reason': reason,
        'x-failure-detail': detail.slice(0, 500),
        'x-failed-at': new Date().toISOString(),
      },
    });
  }
}
