import { Logger, Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { Resend } from 'resend';
import { EmailSenderPort } from '../../application/ports/email-sender.port';
import { LogEmailAdapter } from './log-email.adapter';
import { ResendEmailAdapter } from './resend-email.adapter';
import { RESEND_CLIENT, ResendModule } from './resend.module';

@Module({
  imports: [ResendModule],
  providers: [
    {
      provide: EmailSenderPort,
      inject: [ConfigService, RESEND_CLIENT],
      useFactory: (config: ConfigService, resend: Resend): EmailSenderPort => {
        const driver = config.get<string>('EMAIL_DRIVER', 'log');
        Logger.log(`Driver de correo: ${driver}`, 'EmailModule');
        if (driver !== 'resend') {
          return new LogEmailAdapter();
        }
        return new ResendEmailAdapter(resend, {
          from: config.getOrThrow<string>('MAIL_FROM'),
          replyTo: config.get<string>('MAIL_REPLY_TO') || undefined,
          redirectTo: config.get<string>('MAIL_REDIRECT_TO') || undefined,
        });
      },
    },
  ],
  exports: [EmailSenderPort],
})
export class EmailModule {}
