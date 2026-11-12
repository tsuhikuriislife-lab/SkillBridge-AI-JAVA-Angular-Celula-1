import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Handlebars from 'handlebars';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { TemplateRendererPort } from '../application/ports/template-renderer.port';
import {
  TEMPLATE_KEYS,
  type RenderedEmail,
  type TemplateKey,
} from '../domain/notification';
import type { NotificationEvent } from '../domain/notification-event';
import { htmlToText } from './html-to-text';
import { buildTemplateContext } from './template-context';
import { TEMPLATE_CATALOG } from './template-catalog';

type Compiled = Handlebars.TemplateDelegate<Record<string, unknown>>;

interface CompiledTemplate {
  body: Compiled;
  subject: Compiled;
  preheader: Compiled;
}

const VIEWS_DIR = join(__dirname, 'views');
const read = (...parts: string[]): string =>
  readFileSync(join(VIEWS_DIR, ...parts), 'utf8');

/**
 * Motor de plantillas (CA 1.3): compila TODAS las plantillas .hbs una sola vez al arrancar.
 * Si falta un archivo, el servicio no arranca (falla rápido).
 */
@Injectable()
export class HandlebarsTemplateRenderer implements TemplateRendererPort {
  private readonly hbs = Handlebars.create();
  private readonly layout: Compiled;
  private readonly templates = new Map<TemplateKey, CompiledTemplate>();
  private readonly frontendUrl: string;

  constructor(config: ConfigService) {
    this.frontendUrl = config
      .get<string>('FRONTEND_URL', 'http://localhost:8088')
      .replace(/\/+$/, '');

    this.hbs.registerPartial('button', read('partials', 'button.hbs'));
    this.hbs.registerPartial('details', read('partials', 'details.hbs'));
    this.layout = this.hbs.compile(read('layout.hbs'), { strict: true });

    for (const key of TEMPLATE_KEYS) {
      const entry = TEMPLATE_CATALOG[key];
      this.templates.set(key, {
        body: this.hbs.compile(read(`${entry.view}.hbs`), { strict: true }),
        // El asunto es texto plano: noEscape evita que "&" se convierta en "&amp;"
        subject: this.hbs.compile(entry.subject, {
          strict: true,
          noEscape: true,
        }),
        preheader: this.hbs.compile(entry.preheader, { strict: true }),
      });
    }
  }

  render(
    template: TemplateKey,
    event: NotificationEvent,
    recipientName: string,
  ): RenderedEmail {
    const compiled = this.templates.get(template);
    if (!compiled) {
      throw new Error(`Plantilla no registrada: ${template}`);
    }
    const context = buildTemplateContext(
      event,
      recipientName,
      this.frontendUrl,
    );
    const subject = compiled.subject(context);
    const body = compiled.body(context);
    const html = this.layout({
      subject,
      preheader: compiled.preheader(context),
      body,
    });
    return { subject, html, text: htmlToText(body) };
  }
}
