import {
  BadRequestException,
  Controller,
  Headers,
  HttpCode,
  Inject,
  Logger,
  Post,
  Req,
  ServiceUnavailableException,
  UnauthorizedException,
  type RawBodyRequest,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
  ApiHeader,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
} from '@nestjs/swagger';
import type { Request } from 'express';
import type { Resend } from 'resend';
import {
  ProcessDeliveryEventUseCase,
  type DeliveryWebhookEvent,
} from '../../application/process-delivery-event.use-case';
import { RESEND_CLIENT } from '../email/resend.module';

@ApiTags('webhooks')
@Controller('webhooks')
export class ResendWebhookController {
  private readonly logger = new Logger(ResendWebhookController.name);
  private readonly webhookSecret: string;

  constructor(
    @Inject(RESEND_CLIENT) private readonly resend: Resend,
    private readonly processDelivery: ProcessDeliveryEventUseCase,
    config: ConfigService,
  ) {
    this.webhookSecret = config.get<string>('RESEND_WEBHOOK_SECRET', '');
  }

  @Post('resend')
  @HttpCode(200)
  @ApiOperation({
    summary:
      'Recibe eventos de entrega de Resend (sent, delivered, bounced, complained…)',
    description:
      'Verifica la firma Svix con RESEND_WEBHOOK_SECRET y actualiza NotificationLog.',
  })
  @ApiHeader({ name: 'svix-id', required: true })
  @ApiHeader({ name: 'svix-timestamp', required: true })
  @ApiHeader({ name: 'svix-signature', required: true })
  @ApiOkResponse({ description: 'Evento recibido (aunque se ignore).' })
  async handle(
    @Req() req: RawBodyRequest<Request>,
    @Headers('svix-id') id?: string,
    @Headers('svix-timestamp') timestamp?: string,
    @Headers('svix-signature') signature?: string,
  ): Promise<{ received: boolean; result: string }> {
    if (!this.webhookSecret) {
      throw new ServiceUnavailableException(
        'RESEND_WEBHOOK_SECRET no está configurado',
      );
    }
    const payload = req.rawBody?.toString('utf8');
    if (!payload || !id || !timestamp || !signature) {
      throw new BadRequestException('Faltan el cuerpo o las cabeceras svix-*');
    }

    let event: DeliveryWebhookEvent;
    try {
      event = this.resend.webhooks.verify({
        payload,
        headers: { id, timestamp, signature },
        webhookSecret: this.webhookSecret,
      }) as unknown as DeliveryWebhookEvent;
    } catch {
      this.logger.warn(`Webhook rechazado: firma inválida (svix-id=${id})`);
      throw new UnauthorizedException('Firma del webhook inválida');
    }

    const result = await this.processDelivery.execute(event);
    this.logger.log(
      `Webhook ${event.type} email_id=${event.data?.email_id ?? '-'} → ${result}`,
    );
    return { received: true, result };
  }
}
