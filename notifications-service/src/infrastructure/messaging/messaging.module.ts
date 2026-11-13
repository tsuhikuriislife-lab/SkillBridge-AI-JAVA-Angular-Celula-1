import {
  MessageHandlerErrorBehavior,
  RabbitMQModule,
} from '@golevelup/nestjs-rabbitmq';
import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { HandleNotificationEventUseCase } from '../../application/handle-notification-event.use-case';
import { IdempotencyPort } from '../../application/ports/idempotency.port';
import { TemplatesModule } from '../../templates/templates.module';
import { EmailModule } from '../email/email.module';
import { PersistenceModule } from '../persistence/persistence.module';
import { MetricsController } from './metrics/metrics.controller';
import { MetricsService } from './metrics/metrics.service';
import { NotificationEventsConsumer } from './notification-events.consumer';
import { EVENTS_EXCHANGE } from './rabbit.constants';
import { RabbitTopologyService } from './rabbit-topology.service';
import { RedisIdempotencyAdapter } from './redis/redis-idempotency.adapter';
import {
  REDIS_CLIENT,
  redisClientProvider,
  RedisShutdown,
} from './redis/redis.provider';

@Module({
  imports: [
    RabbitMQModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        uri: config.getOrThrow<string>('RABBITMQ_URL'),
        exchanges: [
          { name: EVENTS_EXCHANGE, type: 'topic', options: { durable: true } },
        ],
        prefetchCount: Number(config.get('RABBITMQ_PREFETCH', 5)),
        defaultSubscribeErrorBehavior: MessageHandlerErrorBehavior.NACK,
        connectionInitOptions: { wait: true, timeout: 30000 },
      }),
    }),
    EmailModule,
    TemplatesModule,
    PersistenceModule,
  ],
  controllers: [MetricsController],
  providers: [
    redisClientProvider,
    RedisShutdown,
    { provide: IdempotencyPort, useClass: RedisIdempotencyAdapter },
    MetricsService,
    RabbitTopologyService,
    HandleNotificationEventUseCase,
    NotificationEventsConsumer,
  ],
  // Leonardo usa estos dos en /health (KAN-58)
  exports: [RabbitMQModule, REDIS_CLIENT],
})
export class MessagingModule {}
