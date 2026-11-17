import type { RenderedEmail, TemplateKey } from '../../domain/notification';
import type { NotificationEvent } from '../../domain/notification-event';

/** Puerto de salida. Implementación final: HandlebarsTemplateRenderer. */
export abstract class TemplateRendererPort {
  abstract render(
    template: TemplateKey,
    event: NotificationEvent,
    recipientName: string,
  ): RenderedEmail;
}
