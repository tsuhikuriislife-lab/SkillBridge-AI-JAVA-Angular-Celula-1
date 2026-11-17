import { Inject, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type Redis from 'ioredis';
import {
  IdempotencyPort,
  type ClaimResult,
} from '../../../application/ports/idempotency.port';
import { REDIS_CLIENT } from './redis.provider';

const PREFIX = 'notifications:idempotency:';
/** Si el proceso muere a mitad de un envío, la reserva expira sola en 5 min. */
const PROCESSING_TTL_SECONDS = 300;

@Injectable()
export class RedisIdempotencyAdapter implements IdempotencyPort {
  private readonly sentTtlSeconds: number;

  constructor(
    @Inject(REDIS_CLIENT) private readonly redis: Redis,
    config: ConfigService,
  ) {
    this.sentTtlSeconds = Number(config.get('IDEMPOTENCY_TTL_SECONDS', 604800));
  }

  async claim(key: string): Promise<ClaimResult> {
    const redisKey = PREFIX + key;
    // SET ... NX: sólo escribe si la llave NO existe. Es atómico: dos consumidores
    // a la vez nunca pueden "ganar" los dos.
    const result = await this.redis.set(
      redisKey,
      'processing',
      'EX',
      PROCESSING_TTL_SECONDS,
      'NX',
    );
    if (result === 'OK') return 'claimed';
    const current = await this.redis.get(redisKey);
    return current === 'sent' ? 'already_sent' : 'in_progress';
  }

  async markSent(key: string): Promise<void> {
    await this.redis.set(PREFIX + key, 'sent', 'EX', this.sentTtlSeconds);
  }

  async release(key: string): Promise<void> {
    await this.redis.del(PREFIX + key);
  }
}
