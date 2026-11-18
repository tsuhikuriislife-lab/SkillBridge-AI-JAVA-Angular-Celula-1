import { ConfigService } from '@nestjs/config';
import { TEMPLATE_KEYS } from '../domain/notification';
import type { NotificationEvent } from '../domain/notification-event';
import { FIXTURE_NAMES, loadFixture } from '../test-support/load-fixture';
import { HandlebarsTemplateRenderer } from './handlebars-template.renderer';

const renderer = new HandlebarsTemplateRenderer(
  new ConfigService({ FRONTEND_URL: 'https://app.test/' }),
);
const fixture = (name: (typeof FIXTURE_NAMES)[number]): NotificationEvent =>
  loadFixture(name) as NotificationEvent;

describe('HandlebarsTemplateRenderer', () => {
  it.each(FIXTURE_NAMES)(
    'renderiza todas las plantillas con el evento %s sin variables faltantes',
    (name) => {
      const event = fixture(name);
      for (const key of TEMPLATE_KEYS) {
        const email = renderer.render(key, event, 'Administrador SkillBridge');
        expect(email.subject.length).toBeGreaterThan(5);
        expect(email.html).toContain('<!doctype html>');
        expect(email.html).not.toContain('{{');
        expect(email.text).not.toMatch(/<[a-z]/i);
        expect(email.html).not.toContain('app.test//');
      }
    },
  );

  it('escapa HTML en los datos del usuario (anti-inyección)', () => {
    const event = fixture('user.registered');
    event.subject.name = '<script>alert(1)</script>';
    const email = renderer.render('welcome', event, event.subject.name);
    expect(email.html).not.toContain('<script>');
    expect(email.html).toContain('&lt;script&gt;');
  });

  it('no escapa el asunto (es texto plano)', () => {
    const event = fixture('enrollment.created');
    event.data.serviceName = 'Java & Spring';
    expect(renderer.render('booking-confirmed', event, 'Ana').subject).toBe(
      'Reserva confirmada: Java & Spring',
    );
  });

  it('el correo de cuenta desactivada no incluye botón de login', () => {
    const event = fixture('user.status.changed');
    const email = renderer.render('status-changed', event, event.subject.name);
    expect(email.subject).toContain('desactivada');
    expect(email.html).not.toContain('/login');
  });

  it('la confirmación de reserva muestra la fecha en español sin correrse de día', () => {
    const email = renderer.render(
      'booking-confirmed',
      fixture('enrollment.created'),
      'Ana',
    );
    expect(email.html).toContain('20 de noviembre de 2026');
    expect(email.text).toContain('Ver mis reservas: https://app.test/bookings');
  });

  it('nunca menciona contraseñas concretas en account-created', () => {
    const event = fixture('user.created');
    const email = renderer.render('account-created', event, event.subject.name);
    expect(email.text).not.toMatch(/TemporaryPass/i);
  });
});
