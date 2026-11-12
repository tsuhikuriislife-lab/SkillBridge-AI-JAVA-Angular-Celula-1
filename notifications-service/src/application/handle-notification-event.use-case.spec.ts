import { LogEmailAdapter } from '../infrastructure/email/log-email.adapter';
import { parseNotificationEvent } from '../infrastructure/messaging/notification-event.dto';
import { InMemoryIdempotencyAdapter } from '../infrastructure/messaging/redis/in-memory-idempotency.adapter';
import { InMemoryNotificationLogAdapter } from '../infrastructure/persistence/in-memory-notification-log.adapter';
import { BasicTemplateRenderer } from '../templates/basic-template.renderer';
import { loadFixture } from '../test-support/load-fixture';
import { HandleNotificationEventUseCase } from './handle-notification-event.use-case';
import {
  PermanentEmailError,
  RetryableEmailError,
} from './ports/email-sender.port';

describe('HandleNotificationEventUseCase', () => {
  let sender: LogEmailAdapter;
  let idempotency: InMemoryIdempotencyAdapter;
  let log: InMemoryNotificationLogAdapter;
  let useCase: HandleNotificationEventUseCase;

  beforeEach(() => {
    sender = new LogEmailAdapter();
    idempotency = new InMemoryIdempotencyAdapter();
    log = new InMemoryNotificationLogAdapter();
    useCase = new HandleNotificationEventUseCase(
      sender,
      new BasicTemplateRenderer(),
      idempotency,
      log,
    );
  });

  it('envía al usuario y al admin, y registra ambos en NotificationLog', async () => {
    const event = await parseNotificationEvent(loadFixture('user.registered'));

    await expect(useCase.execute(event)).resolves.toEqual({
      sent: 2,
      skipped: 0,
    });
    expect(sender.sent.map((e) => e.to)).toEqual([
      'delivered@resend.dev',
      'delivered+admin@resend.dev',
    ]);
    expect(log.records.map((r) => r.status)).toEqual(['sent', 'sent']);
  });

  it('no reenvía si el mismo evento llega dos veces (idempotencia)', async () => {
    const event = await parseNotificationEvent(loadFixture('user.registered'));
    await useCase.execute(event);

    await expect(useCase.execute(event)).resolves.toEqual({
      sent: 0,
      skipped: 2,
    });
    expect(sender.sent).toHaveLength(2);
  });

  it('libera la llave si el envío falla, para poder reintentar', async () => {
    jest
      .spyOn(sender, 'send')
      .mockRejectedValueOnce(new RetryableEmailError('429'));
    const event = await parseNotificationEvent(loadFixture('user.created'));

    await expect(useCase.execute(event)).rejects.toBeInstanceOf(
      RetryableEmailError,
    );
    expect(idempotency.keys.size).toBe(0);
  });

  it('registra el error permanente como "failed"', async () => {
    jest
      .spyOn(sender, 'send')
      .mockRejectedValueOnce(new PermanentEmailError('validation_error'));
    const event = await parseNotificationEvent(loadFixture('user.created'));

    await expect(useCase.execute(event)).rejects.toBeInstanceOf(
      PermanentEmailError,
    );
    expect(log.records[0]).toMatchObject({
      status: 'failed',
      error: 'validation_error',
    });
  });

  it('si otro proceso está enviando el mismo correo, pide reintentar más tarde', async () => {
    const event = await parseNotificationEvent(loadFixture('user.created'));
    idempotency.keys.set(
      `${event.eventId}:account-created:${event.subject.email}`,
      'processing',
    );

    await expect(useCase.execute(event)).rejects.toBeInstanceOf(
      RetryableEmailError,
    );
  });
});
