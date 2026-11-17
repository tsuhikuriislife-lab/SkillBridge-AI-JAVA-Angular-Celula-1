/** Resultado de intentar "reservar" un envío. */
export type ClaimResult = 'claimed' | 'already_sent' | 'in_progress';

/**
 * Puerto de salida: control de idempotencia (CA 2.2).
 * Garantiza que cada correo (llave = eventId:plantilla:destinatario) se envíe UNA sola vez.
 */
export abstract class IdempotencyPort {
  /** Reserva la llave. 'claimed' = puedes enviar; 'already_sent' = ya se envió; 'in_progress' = otro proceso lo está enviando. */
  abstract claim(key: string): Promise<ClaimResult>;
  /** Marca la llave como enviada (se recuerda IDEMPOTENCY_TTL_SECONDS). */
  abstract markSent(key: string): Promise<void>;
  /** Libera la reserva si el envío falló, para que el reintento pueda volver a intentarlo. */
  abstract release(key: string): Promise<void>;
}
