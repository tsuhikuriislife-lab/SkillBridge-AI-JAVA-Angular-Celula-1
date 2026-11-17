import {
  Inject,
  Injectable,
  Logger,
  type OnApplicationShutdown,
  type Provider,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Redis from 'ioredis';

/** Token de inyección del cliente Redis. Uso: @Inject(REDIS_CLIENT) private readonly redis: Redis */
export const REDIS_CLIENT = Symbol('REDIS_CLIENT');

export const redisClientProvider: Provider = {
  provide: REDIS_CLIENT,
  inject: [ConfigService],
  useFactory: (config: ConfigService): Redis => {
    const client = new Redis(config.getOrThrow<string>('REDIS_URL'), {
      maxRetriesPerRequest: 3,
    });
    const logger = new Logger('Redis');
    client.on('error', (error: Error) =>
      logger.error(`Redis: ${error.message}`),
    );
    return client;
  },
};

/** Cierra la conexión a Redis al apagar el servicio (SIGTERM de Docker). */
@Injectable()
export class RedisShutdown implements OnApplicationShutdown {
  constructor(@Inject(REDIS_CLIENT) private readonly redis: Redis) {}

  async onApplicationShutdown(): Promise<void> {
    await this.redis.quit();
  }
}
