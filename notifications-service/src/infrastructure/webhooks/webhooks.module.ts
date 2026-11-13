import { Module } from '@nestjs/common';
import { ProcessDeliveryEventUseCase } from '../../application/process-delivery-event.use-case';
import { PersistenceModule } from '../persistence/persistence.module';
import { ResendWebhookController } from './resend-webhook.controller';

@Module({
  imports: [PersistenceModule],
  controllers: [ResendWebhookController],
  providers: [ProcessDeliveryEventUseCase],
})
export class WebhooksModule {}
