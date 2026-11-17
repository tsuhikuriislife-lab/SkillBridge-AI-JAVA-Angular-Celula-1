import { Global, Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Resend } from 'resend';

/** Token de inyección del cliente de Resend. Uso: @Inject(RESEND_CLIENT) private readonly resend: Resend */
export const RESEND_CLIENT = Symbol('RESEND_CLIENT');

/**
 * Encapsula el SDK de Resend (CA 1.2). Global: lo usan el envío (Dylan),
 * la verificación de webhooks y el health check (Leonardo).
 */
@Global()
@Module({
  providers: [
    {
      provide: RESEND_CLIENT,
      inject: [ConfigService],
      useFactory: (config: ConfigService): Resend =>
        // El SDK exige una llave al construirse. Con EMAIL_DRIVER=log no se envía nada,
        // así que un valor de relleno es seguro.
        new Resend(config.get<string>('RESEND_API_KEY') || 're_not_configured'),
    },
  ],
  exports: [RESEND_CLIENT],
})
export class ResendModule {}
