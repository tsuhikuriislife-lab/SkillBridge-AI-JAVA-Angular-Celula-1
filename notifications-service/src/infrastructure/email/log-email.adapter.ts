import { Injectable, Logger } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import type { OutgoingEmail } from '../../domain/notification';
import {
  EmailSenderPort,
  type SentEmail,
} from '../../application/ports/email-sender.port';
import { maskEmail } from '../../shared/mask-email';

/**
 * EMAIL_DRIVER=log → no envía nada, sólo registra. Útil en desarrollo, CI y pruebas.
 * Guarda los correos en memoria (`sent`) para inspeccionarlos en tests.
 */
@Injectable()
export class LogEmailAdapter implements EmailSenderPort {
  private readonly logger = new Logger(LogEmailAdapter.name);
  readonly sent: OutgoingEmail[] = [];

  send(email: OutgoingEmail): Promise<SentEmail> {
    this.sent.push(email);
    this.logger.log(
      `[EMAIL_DRIVER=log] to=${maskEmail(email.to)} subject="${email.subject}" key=${email.idempotencyKey}`,
    );
    return Promise.resolve({ id: `log-${randomUUID()}` });
  }
}
