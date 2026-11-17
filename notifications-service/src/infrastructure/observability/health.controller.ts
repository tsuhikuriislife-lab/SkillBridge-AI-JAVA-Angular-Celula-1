import { Controller, Get } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import {
  HealthCheck,
  HealthCheckService,
  TypeOrmHealthIndicator,
} from '@nestjs/terminus';
import { RabbitMqHealthIndicator } from './indicators/rabbitmq.health';
import { RedisHealthIndicator } from './indicators/redis.health';
import { ResendHealthIndicator } from './indicators/resend.health';

@ApiTags('health')
@Controller('health')
export class HealthController {
  constructor(
    private readonly health: HealthCheckService,
    private readonly database: TypeOrmHealthIndicator,
    private readonly rabbitmq: RabbitMqHealthIndicator,
    private readonly redis: RedisHealthIndicator,
    private readonly resend: ResendHealthIndicator,
  ) {}

  @Get()
  @HealthCheck()
  @ApiOperation({
    summary: 'Estado de RabbitMQ, Redis, PostgreSQL y Resend (CA 3.4)',
  })
  check() {
    return this.health.check([
      () => this.rabbitmq.check(),
      () => this.redis.check(),
      () => this.database.pingCheck('database', { timeout: 1500 }),
      () => this.resend.check(),
    ]);
  }
}
