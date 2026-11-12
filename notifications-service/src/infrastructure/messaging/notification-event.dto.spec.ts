import { FIXTURE_NAMES, loadFixture } from '../../test-support/load-fixture';
import {
  InvalidNotificationEventError,
  parseNotificationEvent,
} from './notification-event.dto';

describe('parseNotificationEvent', () => {
  it.each(FIXTURE_NAMES)('acepta el fixture del contrato %s', async (name) => {
    await expect(
      parseNotificationEvent(loadFixture(name)),
    ).resolves.toMatchObject({
      eventType: name,
    });
  });

  it('rechaza un email inválido en subject', async () => {
    const bad = loadFixture('user.created') as { subject: { email: string } };
    bad.subject.email = 'no-es-un-correo';
    await expect(parseNotificationEvent(bad)).rejects.toThrow(/subject\.email/);
  });

  it('rechaza un eventType desconocido', async () => {
    const bad = {
      ...(loadFixture('user.created') as object),
      eventType: 'user.hacked',
    };
    await expect(parseNotificationEvent(bad)).rejects.toBeInstanceOf(
      InvalidNotificationEventError,
    );
  });

  it('exige serviceName en enrollment.created', async () => {
    const bad = loadFixture('enrollment.created') as {
      data: Record<string, string>;
    };
    delete bad.data.serviceName;
    await expect(parseNotificationEvent(bad)).rejects.toThrow(/serviceName/);
  });

  it('rechaza algo que no es un objeto', async () => {
    await expect(parseNotificationEvent('hola')).rejects.toBeInstanceOf(
      InvalidNotificationEventError,
    );
  });
});
