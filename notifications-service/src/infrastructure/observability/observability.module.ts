import { Module } from '@nestjs/common';
import { TerminusModule } from '@nestjs/terminus';
import { MessagingModule } from '../messaging/messaging.module';
import { HealthController } from './health.controller';
import { RabbitMqHealthIndicator } from './indicators/rabbitmq.health';
import { RedisHealthIndicator } from './indicators/redis.health';
import { ResendHealthIndicator } from './indicators/resend.health';
import { LivenessController } from './liveness.controller';

@Module({
  imports: [TerminusModule, MessagingModule],
  controllers: [LivenessController, HealthController],
  providers: [
    RabbitMqHealthIndicator,
    RedisHealthIndicator,
    ResendHealthIndicator,
  ],
})
export class ObservabilityModule {}
