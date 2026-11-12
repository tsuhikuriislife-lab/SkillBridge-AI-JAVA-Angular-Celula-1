import { Module } from '@nestjs/common';
import { TemplateRendererPort } from '../application/ports/template-renderer.port';
import { HandlebarsTemplateRenderer } from './handlebars-template.renderer';

@Module({
  providers: [
    { provide: TemplateRendererPort, useClass: HandlebarsTemplateRenderer },
  ],
  exports: [TemplateRendererPort],
})
export class TemplatesModule {}
