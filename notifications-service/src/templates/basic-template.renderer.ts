import { Injectable } from '@nestjs/common';
import type { RenderedEmail, TemplateKey } from '../domain/notification';
import type { NotificationEvent } from '../domain/notification-event';
import { TemplateRendererPort } from '../application/ports/template-renderer.port';

/** Renderer mínimo (texto plano). Se usa en la base y en las pruebas de Kevin. */
@Injectable()
export class BasicTemplateRenderer implements TemplateRendererPort {
  render(
    template: TemplateKey,
    event: NotificationEvent,
    recipientName: string,
  ): RenderedEmail {
    const text = `Hola ${recipientName}: evento ${event.eventType} (${template}).`;
    return {
      subject: `[SkillBridge AI] ${template}`,
      text,
      html: `<p>${text}</p>`,
    };
  }
}
