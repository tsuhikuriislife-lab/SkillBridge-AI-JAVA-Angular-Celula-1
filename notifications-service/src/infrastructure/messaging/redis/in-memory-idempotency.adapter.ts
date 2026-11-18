import { Injectable } from '@nestjs/common';
import {
  IdempotencyPort,
  type ClaimResult,
} from '../../../application/ports/idempotency.port';

/** Implementación en memoria para pruebas unitarias (mismo comportamiento que Redis). */
@Injectable()
export class InMemoryIdempotencyAdapter implements IdempotencyPort {
  readonly keys = new Map<string, 'processing' | 'sent'>();

  claim(key: string): Promise<ClaimResult> {
    const current = this.keys.get(key);
    if (current === 'sent') return Promise.resolve('already_sent');
    if (current === 'processing') return Promise.resolve('in_progress');
    this.keys.set(key, 'processing');
    return Promise.resolve('claimed');
  }

  markSent(key: string): Promise<void> {
    this.keys.set(key, 'sent');
    return Promise.resolve();
  }

  release(key: string): Promise<void> {
    this.keys.delete(key);
    return Promise.resolve();
  }
}
