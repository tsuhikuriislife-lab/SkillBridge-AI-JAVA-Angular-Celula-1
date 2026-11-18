import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/** Ruta a docs/notifications/contract desde notifications-service/src/test-support */
export const CONTRACT_DIR = join(
  __dirname,
  '..',
  '..',
  '..',
  'docs',
  'notifications',
  'contract',
);

export const FIXTURE_NAMES = [
  'user.registered',
  'user.created',
  'user.status.changed',
  'user.role.changed',
  'user.deleted',
  'enrollment.created',
] as const;

export type FixtureName = (typeof FIXTURE_NAMES)[number];

export function loadFixture(name: FixtureName): unknown {
  return JSON.parse(
    readFileSync(join(CONTRACT_DIR, `${name}.json`), 'utf8'),
  ) as unknown;
}
