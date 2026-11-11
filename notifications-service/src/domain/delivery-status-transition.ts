import type { DeliveryStatus } from './delivery-status';

/**
 * Orden del ciclo de vida. Los webhooks pueden llegar DESORDENADOS
 * (p. ej. "delivered" antes que "sent"): nunca se retrocede a un estado anterior.
 */
const RANK: Record<DeliveryStatus, number> = {
  sent: 0,
  delivery_delayed: 1,
  delivered: 2,
  bounced: 3,
  complained: 3,
  failed: 3,
};

export function canTransition(
  current: DeliveryStatus,
  next: DeliveryStatus,
): boolean {
  return next !== current && RANK[next] >= RANK[current];
}
