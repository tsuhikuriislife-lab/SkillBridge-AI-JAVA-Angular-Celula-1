import { UnauthorizedException, type RawBodyRequest } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { Request } from 'express';
import type { Resend } from 'resend';
import type { ProcessDeliveryEventUseCase } from '../../application/process-delivery-event.use-case';
import { ResendWebhookController } from './resend-webhook.controller';

describe('ResendWebhookController', () => {
  const body = JSON.stringify({
    type: 'email.delivered',
    data: { email_id: 're_1' },
  });
  const req = { rawBody: Buffer.from(body) } as RawBodyRequest<Request>;
  let verify: jest.Mock;
  let execute: jest.Mock;
  let controller: ResendWebhookController;

  beforeEach(() => {
    verify = jest.fn().mockReturnValue(JSON.parse(body));
    execute = jest.fn().mockResolvedValue('updated');
    controller = new ResendWebhookController(
      { webhooks: { verify } } as unknown as Resend,
      { execute } as unknown as ProcessDeliveryEventUseCase,
      new ConfigService({ RESEND_WEBHOOK_SECRET: 'whsec_test' }),
    );
  });

  it('verifica la firma con el cuerpo CRUDO y procesa el evento', async () => {
    await expect(
      controller.handle(req, 'msg_1', '1731337200', 'v1,abc'),
    ).resolves.toEqual({
      received: true,
      result: 'updated',
    });
    expect(verify).toHaveBeenCalledWith({
      payload: body,
      headers: { id: 'msg_1', timestamp: '1731337200', signature: 'v1,abc' },
      webhookSecret: 'whsec_test',
    });
  });

  it('responde 401 si la firma no es válida', async () => {
    verify.mockImplementation(() => {
      throw new Error('No matching signature found');
    });
    await expect(
      controller.handle(req, 'msg_1', '1', 'v1,mala'),
    ).rejects.toBeInstanceOf(UnauthorizedException);
    expect(execute).not.toHaveBeenCalled();
  });

  it('responde 400 si faltan cabeceras svix', async () => {
    await expect(
      controller.handle(req, undefined, '1', 'v1,abc'),
    ).rejects.toThrow(/svix/);
  });
});
