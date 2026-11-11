import { Module } from '@nestjs/common';
import { TemplateRendererPort } from '../application/ports/template-renderer.port';
import { BasicTemplateRenderer } from './basic-template.renderer';

/** BASE: renderer básico. En el Bloque 1 Dylan lo cambia por HandlebarsTemplateRenderer. */
@Module({
  providers: [
    { provide: TemplateRendererPort, useClass: BasicTemplateRenderer },
  ],
  exports: [TemplateRendererPort],
})
export class TemplatesModule {}
