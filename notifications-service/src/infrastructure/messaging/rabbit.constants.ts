/** Topología RabbitMQ — docs/notifications/CONTRACT.md §1 */
export const EVENTS_EXCHANGE = 'skillbridge.events';
export const ROUTING_KEYS = ['user.#', 'enrollment.#'];
export const EVENTS_QUEUE = 'notifications.events';
export const DEAD_LETTER_QUEUE = 'dlq.notifications';
export const RETRY_COUNT_HEADER = 'x-retry-count';

/**
 * Reintentos exponenciales (CA 2.3): una cola de espera por intento, con TTL creciente.
 * Al vencer el TTL, RabbitMQ devuelve el mensaje a EVENTS_QUEUE.
 */
export const RETRY_QUEUES = [
  { name: 'notifications.retry.5s', ttlMs: 5_000 },
  { name: 'notifications.retry.30s', ttlMs: 30_000 },
  { name: 'notifications.retry.2m', ttlMs: 120_000 },
] as const;

export const MAX_RETRIES = RETRY_QUEUES.length;
