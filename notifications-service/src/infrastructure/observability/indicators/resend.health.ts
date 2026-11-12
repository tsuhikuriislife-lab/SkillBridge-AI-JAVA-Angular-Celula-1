import { Inject, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { HealthIndicatorService } from '@nestjs/terminus';
import type { Resend } from 'resend';
import { RESEND_CLIENT } from '../../email/resend.module';

/** Resend limita las peticiones: se consulta como máximo una vez por minuto. */
const CACHE_MS = 60_000;

@Injectable()
export class ResendHealthIndicator {
  private lastCheck: { at: number; ok: boolean; message: string } | null = null;
  private readonly driver: string;

  constructor(
    @Inject(RESEND_CLIENT) private readonly resend: Pick<Resend, 'domains'>,
    private readonly health: HealthIndicatorService,
    config: ConfigService,
  ) {
    this.driver = config.get<string>('EMAIL_DRIVER', 'log');
  }

  async check(key = 'resend') {
    const indicator = this.health.check(key);
    if (this.driver !== 'resend') {
      return indicator.up({ mode: 'log (sin envío real)' });
    }
    if (!this.lastCheck || Date.now() - this.lastCheck.at > CACHE_MS) {
      try {
        const { error } = await this.resend.domains.list();
        this.lastCheck = {
          at: Date.now(),
          ok: !error,
          message: error?.message ?? 'ok',
        };
      } catch (error) {
        this.lastCheck = {
          at: Date.now(),
          ok: false,
          message: error instanceof Error ? error.message : String(error),
        };
      }
    }
    return this.lastCheck.ok
      ? indicator.up()
      : indicator.down({ message: this.lastCheck.message });
  }
}
