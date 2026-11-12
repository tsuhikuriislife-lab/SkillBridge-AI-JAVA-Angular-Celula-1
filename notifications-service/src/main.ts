import { Logger, ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import { setupSwagger } from './infrastructure/http/swagger';

async function bootstrap(): Promise<void> {
  // rawBody: true → guarda el cuerpo original de cada petición (necesario para verificar la firma de los webhooks de Resend)
  const app = await NestFactory.create(AppModule, { rawBody: true });
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, transform: true }));
  app.enableShutdownHooks();
  setupSwagger(app);
  const port = Number(app.get(ConfigService).get('PORT', 3100));
  await app.listen(port);
  Logger.log(
    `notifications-service escuchando en el puerto ${port}`,
    'Bootstrap',
  );
}

void bootstrap();
