/* eslint-disable @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-unsafe-member-access */
import type { AmqpConnection } from '@golevelup/nestjs-rabbitmq';
import type { ConsumeMessage } from 'amqplib';
import type { HandleNotificationEventUseCase } from '../../application/handle-notification-event.use-case';
import {
  PermanentEmailError,
  RetryableEmailError,
} from '../../application/ports/email-sender.port';
import { loadFixture } from '../../test-support/load-fixture';
import { MetricsService } from './metrics/metrics.service';
import { NotificationEventsConsumer } from './notification-events.consumer';
import { DEAD_LETTER_QUEUE, RETRY_QUEUES } from './rabbit.constants';

const message = (headers: Record<string, unknown> = {}): ConsumeMessage =>
  ({
    properties: { headers, messageId: 'msg-1' },
  }) as unknown as ConsumeMessage;

describe('NotificationEventsConsumer', () => {
  const event = loadFixture('user.registered');
  let publish: jest.Mock;
  let execute: jest.Mock;
  let consumer: NotificationEventsConsumer;

  beforeEach(() => {
    publish = jest.fn().mockResolvedValue(true);
    execute = jest.fn().mockResolvedValue({ sent: 2, skipped: 0 });
    consumer = new NotificationEventsConsumer(
      { execute } as unknown as HandleNotificationEventUseCase,
      { publish } as unknown as AmqpConnection,
      new MetricsService(),
    );
  });

  it('procesa un evento válido sin republicar', async () => {
    await consumer.onEvent(event, message());
    expect(execute).toHaveBeenCalledTimes(1);
    expect(publish).not.toHaveBeenCalled();
  });

  it.each([
    [0, RETRY_QUEUES[0].name],
    [1, RETRY_QUEUES[1].name],
    [2, RETRY_QUEUES[2].name],
  ])(
    'con %i reintentos previos manda el fallo transitorio a %s (backoff exponencial)',
    async (previous, queue) => {
      execute.mockRejectedValueOnce(new RetryableEmailError('429'));
      await consumer.onEvent(event, message({ 'x-retry-count': previous }));
      expect(publish).toHaveBeenCalledWith(
        '',
        queue,
        event,
        expect.objectContaining({
          headers: expect.objectContaining({ 'x-retry-count': previous + 1 }),
        }),
      );
    },
  );

  it('manda a la DLQ cuando se agotan los 3 reintentos', async () => {
    execute.mockRejectedValueOnce(new RetryableEmailError('500'));
    await consumer.onEvent(event, message({ 'x-retry-count': 3 }));
    expect(publish).toHaveBeenCalledWith(
      '',
      DEAD_LETTER_QUEUE,
      event,
      expect.objectContaining({
        headers: expect.objectContaining({
          'x-failure-reason': 'max_retries_exceeded',
        }),
      }),
    );
  });

  it('manda a la DLQ sin reintentar un error permanente', async () => {
    execute.mockRejectedValueOnce(new PermanentEmailError('validation_error'));
    await consumer.onEvent(event, message());
    expect(publish).toHaveBeenCalledTimes(1);
    expect(publish.mock.calls[0][1]).toBe(DEAD_LETTER_QUEUE);
  });

  it('manda a la DLQ un mensaje que no cumple el contrato', async () => {
    await consumer.onEvent({ foo: 'bar' }, message());
    expect(execute).not.toHaveBeenCalled();
    expect(publish.mock.calls[0][1]).toBe(DEAD_LETTER_QUEUE);
  });
});
