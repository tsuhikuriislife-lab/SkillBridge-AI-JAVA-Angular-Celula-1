import { Logger } from '@nestjs/common';
import type { Resend } from 'resend';
import type { OutgoingEmail } from '../../domain/notification';
import {
  EmailSenderPort,
  PermanentEmailError,
  RetryableEmailError,
  type SentEmail,
} from '../../application/ports/email-sender.port';
import { maskEmail } from '../../shared/mask-email';

/** Errores de Resend que vale la pena reintentar (https://resend.com/docs/api-reference/errors). */
const RETRYABLE_ERRORS = new Set<string>([
  'rate_limit_exceeded',
  'concurrent_idempotent_requests',
  'application_error',
  'internal_server_error',
]);

export interface ResendAdapterOptions {
  from: string;
  replyTo?: string;
  /** Sólo desarrollo: si se define, TODOS los correos van a esta dirección. */
  redirectTo?: string;
}

export type ResendClient = Pick<Resend, 'emails'>;

export class ResendEmailAdapter implements EmailSenderPort {
  private readonly logger = new Logger(ResendEmailAdapter.name);

  constructor(
    private readonly client: ResendClient,
    private readonly options: ResendAdapterOptions,
  ) {
    if (options.redirectTo) {
      this.logger.warn(
        `MAIL_REDIRECT_TO activo: todos los correos irán a ${maskEmail(options.redirectTo)}`,
      );
    }
  }

  async send(email: OutgoingEmail): Promise<SentEmail> {
    const redirect = this.options.redirectTo;
    const to = redirect ?? email.to;
    const subject = redirect
      ? `[para: ${email.to}] ${email.subject}`
      : email.subject;

    let response: Awaited<ReturnType<ResendClient['emails']['send']>>;
    try {
      response = await this.client.emails.send(
        {
          from: this.options.from,
          to: [to],
          subject,
          html: email.html,
          text: email.text,
          replyTo: this.options.replyTo,
          tags: email.tags,
        },
        { idempotencyKey: email.idempotencyKey },
      );
    } catch (error) {
      // Timeout, DNS o conexión rechazada: el problema es la red, no los datos.
      throw new RetryableEmailError(
        `Error de red con Resend: ${error instanceof Error ? error.message : String(error)}`,
      );
    }

    if (response.error) {
      const detail = `${response.error.name}: ${response.error.message}`;
      if (RETRYABLE_ERRORS.has(response.error.name)) {
        throw new RetryableEmailError(detail);
      }
      throw new PermanentEmailError(detail);
    }

    if (!response.data) {
      throw new RetryableEmailError('Resend respondió sin id de correo');
    }

    return { id: response.data.id };
  }
}
