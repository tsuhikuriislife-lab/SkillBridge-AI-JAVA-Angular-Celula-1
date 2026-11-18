import type { INestApplication } from '@nestjs/common';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';

/** Documentación OpenAPI en http://localhost:3100/docs */
export function setupSwagger(app: INestApplication): void {
  const config = new DocumentBuilder()
    .setTitle('SkillBridge AI — Notifications Service')
    .setDescription(
      'Microservicio de notificaciones por correo (RabbitMQ → Resend). Webhooks de entrega y salud.',
    )
    .setVersion('1.0.0')
    .build();
  SwaggerModule.setup('docs', app, SwaggerModule.createDocument(app, config));
}
