import {
  PermanentEmailError,
  RetryableEmailError,
} from '../../application/ports/email-sender.port';
import type { OutgoingEmail } from '../../domain/notification';
import { ResendEmailAdapter, type ResendClient } from './resend-email.adapter';

const email: OutgoingEmail = {
  to: 'delivered@resend.dev',
  subject: 'Hola',
  html: '<p>Hola</p>',
  text: 'Hola',
  idempotencyKey: 'evt-1:welcome:delivered@resend.dev',
  tags: [{ name: 'template', value: 'welcome' }],
};

function adapterWith(send: jest.Mock, redirectTo?: string): ResendEmailAdapter {
  const client = { emails: { send } } as unknown as ResendClient;
  return new ResendEmailAdapter(client, {
    from: 'SkillBridge <no-reply@test.dev>',
    redirectTo,
  });
}

describe('ResendEmailAdapter', () => {
  it('envía con la llave de idempotencia y devuelve el id', async () => {
    const send = jest
      .fn()
      .mockResolvedValue({ data: { id: 're_123' }, error: null });
    await expect(adapterWith(send).send(email)).resolves.toEqual({
      id: 're_123',
    });
    expect(send).toHaveBeenCalledWith(
      expect.objectContaining({
        to: ['delivered@resend.dev'],
        subject: 'Hola',
      }),
      { idempotencyKey: email.idempotencyKey },
    );
  });

  it('redirige todos los correos cuando MAIL_REDIRECT_TO está activo', async () => {
    const send = jest
      .fn()
      .mockResolvedValue({ data: { id: 're_1' }, error: null });
    await adapterWith(send, 'yo@equipo.dev').send(email);
    expect(send).toHaveBeenCalledWith(
      expect.objectContaining({
        to: ['yo@equipo.dev'],
        subject: '[para: delivered@resend.dev] Hola',
      }),
      expect.anything(),
    );
  });

  it('convierte rate_limit_exceeded en error reintentable', async () => {
    const send = jest.fn().mockResolvedValue({
      data: null,
      error: { name: 'rate_limit_exceeded', message: 'Too many requests' },
    });
    await expect(adapterWith(send).send(email)).rejects.toBeInstanceOf(
      RetryableEmailError,
    );
  });

  it('convierte validation_error en error permanente', async () => {
    const send = jest.fn().mockResolvedValue({
      data: null,
      error: { name: 'validation_error', message: 'Invalid `to` field' },
    });
    await expect(adapterWith(send).send(email)).rejects.toBeInstanceOf(
      PermanentEmailError,
    );
  });

  it('trata un fallo de red como reintentable', async () => {
    const send = jest.fn().mockRejectedValue(new Error('ECONNRESET'));
    await expect(adapterWith(send).send(email)).rejects.toBeInstanceOf(
      RetryableEmailError,
    );
  });
});
