import { ConfigService } from '@nestjs/config';
import type Redis from 'ioredis';
import { RedisIdempotencyAdapter } from './redis-idempotency.adapter';

describe('RedisIdempotencyAdapter', () => {
  const redis = { set: jest.fn(), get: jest.fn(), del: jest.fn() };
  const adapter = new RedisIdempotencyAdapter(
    redis as unknown as Redis,
    new ConfigService({ IDEMPOTENCY_TTL_SECONDS: 3600 }),
  );

  beforeEach(() => jest.resetAllMocks());

  it('reserva la llave con SET NX y prefijo propio', async () => {
    redis.set.mockResolvedValue('OK');
    await expect(adapter.claim('evt:welcome:a@b.co')).resolves.toBe('claimed');
    expect(redis.set).toHaveBeenCalledWith(
      'notifications:idempotency:evt:welcome:a@b.co',
      'processing',
      'EX',
      300,
      'NX',
    );
  });

  it('detecta un correo ya enviado', async () => {
    redis.set.mockResolvedValue(null);
    redis.get.mockResolvedValue('sent');
    await expect(adapter.claim('k')).resolves.toBe('already_sent');
  });

  it('detecta un envío en curso', async () => {
    redis.set.mockResolvedValue(null);
    redis.get.mockResolvedValue('processing');
    await expect(adapter.claim('k')).resolves.toBe('in_progress');
  });

  it('marca como enviado con el TTL configurado', async () => {
    await adapter.markSent('k');
    expect(redis.set).toHaveBeenCalledWith(
      'notifications:idempotency:k',
      'sent',
      'EX',
      3600,
    );
  });
});
