import { ConfigService } from '@nestjs/config';
import { HealthIndicatorService } from '@nestjs/terminus';
import { ResendHealthIndicator } from './resend.health';

describe('ResendHealthIndicator', () => {
  const health = new HealthIndicatorService();

  it('con EMAIL_DRIVER=log responde up sin llamar a Resend', async () => {
    const list = jest.fn();
    const indicator = new ResendHealthIndicator(
      { domains: { list } } as never,
      health,
      new ConfigService({ EMAIL_DRIVER: 'log' }),
    );
    await expect(indicator.check()).resolves.toMatchObject({
      resend: { status: 'up' },
    });
    expect(list).not.toHaveBeenCalled();
  });

  it('cachea la consulta a Resend durante 60 s', async () => {
    const list = jest
      .fn()
      .mockResolvedValue({ data: { data: [] }, error: null });
    const indicator = new ResendHealthIndicator(
      { domains: { list } } as never,
      health,
      new ConfigService({ EMAIL_DRIVER: 'resend' }),
    );
    await indicator.check();
    await indicator.check();
    expect(list).toHaveBeenCalledTimes(1);
  });

  it('responde down si la API key es inválida', async () => {
    const list = jest.fn().mockResolvedValue({
      data: null,
      error: { name: 'validation_error', message: 'API key is invalid' },
    });
    const indicator = new ResendHealthIndicator(
      { domains: { list } } as never,
      health,
      new ConfigService({ EMAIL_DRIVER: 'resend' }),
    );
    await expect(indicator.check()).resolves.toMatchObject({
      resend: { status: 'down' },
    });
  });
});
