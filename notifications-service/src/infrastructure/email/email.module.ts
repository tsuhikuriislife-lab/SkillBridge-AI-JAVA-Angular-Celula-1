import { Module } from '@nestjs/common';
import { EmailSenderPort } from '../../application/ports/email-sender.port';
import { LogEmailAdapter } from './log-email.adapter';
import { ResendModule } from './resend.module';

/** BASE: sólo driver "log". En el Bloque 1 Dylan agrega el driver "resend". */
@Module({
  imports: [ResendModule],
  providers: [{ provide: EmailSenderPort, useClass: LogEmailAdapter }],
  exports: [EmailSenderPort],
})
export class EmailModule {}
