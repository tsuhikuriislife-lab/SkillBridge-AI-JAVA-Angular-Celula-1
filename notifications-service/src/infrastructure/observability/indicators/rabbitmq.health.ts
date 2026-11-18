import { AmqpConnection } from '@golevelup/nestjs-rabbitmq';
import { Injectable } from '@nestjs/common';
import { HealthIndicatorService } from '@nestjs/terminus';

@Injectable()
export class RabbitMqHealthIndicator {
  constructor(
    private readonly amqp: AmqpConnection,
    private readonly health: HealthIndicatorService,
  ) {}

  check(key = 'rabbitmq') {
    const indicator = this.health.check(key);
    return this.amqp.managedConnection.isConnected()
      ? indicator.up()
      : indicator.down({ message: 'Sin conexión con RabbitMQ' });
  }
}
