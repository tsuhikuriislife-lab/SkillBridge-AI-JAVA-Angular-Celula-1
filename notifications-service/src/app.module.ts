import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { envValidationSchema } from './config/env.validation';
import { EmailModule } from './infrastructure/email/email.module';
import { MessagingModule } from './infrastructure/messaging/messaging.module';
import { ObservabilityModule } from './infrastructure/observability/observability.module';
import { PersistenceModule } from './infrastructure/persistence/persistence.module';
import { WebhooksModule } from './infrastructure/webhooks/webhooks.module';
import { TemplatesModule } from './templates/templates.module';

/**
 * Este archivo NO se vuelve a modificar después de la base.
 * Cada integrante trabaja SOLO dentro de su módulo:
 *  - Dylan:    EmailModule, TemplatesModule
 *  - Kevin:    MessagingModule
 *  - Leonardo: PersistenceModule, ObservabilityModule, WebhooksModule
 */
@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      validationSchema: envValidationSchema,
    }),
    EmailModule,
    TemplatesModule,
    PersistenceModule,
    MessagingModule,
    ObservabilityModule,
    WebhooksModule,
  ],
})
export class AppModule {}
