import { InMemoryNotificationLogAdapter } from '../infrastructure/persistence/in-memory-notification-log.adapter';
import { ProcessDeliveryEventUseCase } from './process-delivery-event.use-case';

describe('ProcessDeliveryEventUseCase', () => {
  let log: InMemoryNotificationLogAdapter;
  let useCase: ProcessDeliveryEventUseCase;

  beforeEach(async () => {
    log = new InMemoryNotificationLogAdapter();
    useCase = new ProcessDeliveryEventUseCase(log);
    await log.recordSent({
      eventId: 'evt-1',
      eventType: 'user.registered',
      template: 'welcome',
      recipient: 'delivered@resend.dev',
      providerMessageId: 're_123',
    });
  });

  const webhook = (type: string, emailId = 're_123') => ({
    type,
    created_at: '2026-11-11T15:00:05Z',
    data: { email_id: emailId },
  });

  it('actualiza a delivered', async () => {
    await expect(useCase.execute(webhook('email.delivered'))).resolves.toBe(
      'updated',
    );
    expect(log.records[0].status).toBe('delivered');
  });

  it('registra un rebote', async () => {
    await useCase.execute(webhook('email.bounced'));
    expect(log.records[0].status).toBe('bounced');
  });

  it('no retrocede si llega "sent" después de "delivered"', async () => {
    await useCase.execute(webhook('email.delivered'));
    await expect(useCase.execute(webhook('email.sent'))).resolves.toBe('stale');
    expect(log.records[0].status).toBe('delivered');
  });

  it('ignora eventos que no son de entrega (email.opened)', async () => {
    await expect(useCase.execute(webhook('email.opened'))).resolves.toBe(
      'ignored',
    );
  });

  it('informa cuando el correo no está en el log', async () => {
    await expect(
      useCase.execute(webhook('email.delivered', 're_999')),
    ).resolves.toBe('not_found');
  });
});
