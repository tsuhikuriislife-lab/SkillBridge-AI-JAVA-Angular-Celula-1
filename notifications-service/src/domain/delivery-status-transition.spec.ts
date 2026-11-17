import { canTransition } from './delivery-status-transition';

describe('canTransition', () => {
  it.each([
    ['sent', 'delivered', true],
    ['sent', 'bounced', true],
    ['delivery_delayed', 'delivered', true],
    ['delivered', 'complained', true],
    ['delivered', 'sent', false],
    ['bounced', 'delivered', false],
    ['delivered', 'delivered', false],
    // bounced, complained y failed son finales: ningún webhook posterior los cambia
    ['bounced', 'complained', false],
    ['complained', 'bounced', false],
    ['failed', 'bounced', false],
  ] as const)('%s → %s = %s', (current, next, expected) => {
    expect(canTransition(current, next)).toBe(expected);
  });
});
