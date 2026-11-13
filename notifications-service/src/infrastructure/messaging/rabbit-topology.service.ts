import { AmqpConnection } from '@golevelup/nestjs-rabbitmq';
import { Injectable, Logger, type OnModuleInit } from '@nestjs/common';
import {
  DEAD_LETTER_QUEUE,
  EVENTS_QUEUE,
  RETRY_QUEUES,
} from './rabbit.constants';

/**
 * Declara la DLQ y las colas de espera (idempotente: si ya existen igual, no hace nada).
 *
 *  notifications.events ──(fallo n)──► consumidor publica en notifications.retry.<n>
 *  notifications.retry.<n> ──(vence TTL)──► vuelve a notifications.events
 *  dlq.notifications ◄── inválidos / permanentes / reintentos agotados
 */
@Injectable()
export class RabbitTopologyService implements OnModuleInit {
  private readonly logger = new Logger(RabbitTopologyService.name);

  constructor(private readonly amqp: AmqpConnection) {}

  async onModuleInit(): Promise<void> {
    await this.amqp.channel.assertQueue(DEAD_LETTER_QUEUE, { durable: true });
    for (const retry of RETRY_QUEUES) {
      await this.amqp.channel.assertQueue(retry.name, {
        durable: true,
        arguments: {
          'x-message-ttl': retry.ttlMs,
          'x-dead-letter-exchange': '',
          'x-dead-letter-routing-key': EVENTS_QUEUE,
        },
      });
    }
    this.logger.log(
      `Colas listas: ${DEAD_LETTER_QUEUE}, ${RETRY_QUEUES.map((r) => r.name).join(', ')}`,
    );
  }
}
