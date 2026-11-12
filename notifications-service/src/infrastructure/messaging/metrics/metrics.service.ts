import { Injectable } from '@nestjs/common';
import {
  collectDefaultMetrics,
  Counter,
  Histogram,
  Registry,
} from 'prom-client';

/**
 * Los nombres coinciden con el dashboard que YA existe en
 * ops/grafana/provisioning/dashboards/notifications.json ("SkillBridge - Notificaciones").
 */
@Injectable()
export class MetricsService {
  readonly registry = new Registry();

  private readonly processedTotal = new Counter({
    name: 'skillbridge_notifications_processed_total',
    help: 'Eventos procesados con éxito (todos sus correos enviados)',
    labelNames: ['event_type'],
    registers: [this.registry],
  });

  private readonly duplicateTotal = new Counter({
    name: 'skillbridge_notifications_duplicate_total',
    help: 'Eventos cuyos correos ya se habían enviado (re-entregas)',
    labelNames: ['event_type'],
    registers: [this.registry],
  });

  private readonly failedAttemptsTotal = new Counter({
    name: 'skillbridge_notifications_failed_attempts_total',
    help: 'Intentos fallidos (incluye mensajes inválidos)',
    labelNames: ['event_type'],
    registers: [this.registry],
  });

  private readonly processingSeconds = new Histogram({
    name: 'skillbridge_notifications_processing_seconds',
    help: 'Tiempo de procesamiento de un evento',
    buckets: [0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10],
    registers: [this.registry],
  });

  constructor() {
    if (process.env.NODE_ENV !== 'test') {
      collectDefaultMetrics({
        register: this.registry,
        prefix: 'notifications_service_',
      });
    }
  }

  processed(eventType: string): void {
    this.processedTotal.inc({ event_type: eventType });
  }

  duplicate(eventType: string): void {
    this.duplicateTotal.inc({ event_type: eventType });
  }

  failedAttempt(eventType: string): void {
    this.failedAttemptsTotal.inc({ event_type: eventType });
  }

  startProcessingTimer(): () => void {
    const end = this.processingSeconds.startTimer();
    return () => {
      end();
    };
  }
}
