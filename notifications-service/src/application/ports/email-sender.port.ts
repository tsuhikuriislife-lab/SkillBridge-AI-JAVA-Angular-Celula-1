import type { OutgoingEmail } from '../../domain/notification';

export interface SentEmail {
  id: string;
}

/** Puerto de salida. Implementaciones: LogEmailAdapter (dev/test) y ResendEmailAdapter. */
export abstract class EmailSenderPort {
  abstract send(email: OutgoingEmail): Promise<SentEmail>;
}

/** Error transitorio (red, 429, 5xx): vale la pena reintentar. */
export class RetryableEmailError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'RetryableEmailError';
  }
}

/** Error permanente (datos inválidos, cuota agotada): reintentar no sirve → DLQ. */
export class PermanentEmailError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'PermanentEmailError';
  }
}
