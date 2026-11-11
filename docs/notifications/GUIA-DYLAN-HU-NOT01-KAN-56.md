# Guía Dylan: KAN-56 / HU-NOT01, scaffolding NestJS, SDK de Resend y motor de plantillas

> **Jira:** [KAN-56](https://skillbridgec3.atlassian.net/browse/KAN-56) (épica [KAN-49](https://skillbridgec3.atlassian.net/browse/KAN-49))
> **Responsable:** Dylan Suarez
> **Rama:** `feature/notificaciones-nest-base` y luego `feature/notificaciones-resend-templates` (las dos salen de `feature/notificaciones`)
> **Antes de empezar lee:** [CONTRACT.md](./CONTRACT.md) y [PLAN.md](./PLAN.md)
> **Requisitos:** Node.js 22 (`node -v`), npm 10+ y Docker.

## Qué cubre esta guía (criterios de aceptación de KAN-56)

| CA | Qué | Dónde |
|---|---|---|
| CA 1.1 | Proyecto NestJS con TypeScript estricto, Dockerfile multi-stage y servicio en `docker-compose.yml` dentro de la red `skillbridge` | Bloque 0 + Paso 11 |
| CA 1.2 | `ResendModule` desacoplado que encapsula el cliente de Resend con inyección de dependencias | Paso 5 + Paso 8 |
| CA 1.3 | Motor de plantillas **Handlebars**: bienvenida, **confirmación de reserva** y cuenta (recordatorio de sesión: 2ª iteración, ver PLAN) | Paso 9 |
| CA 1.4 | Pruebas de renderizado y mock del cliente Resend | Pasos 8.2 y 9.6 |

> ⚠️ **La base (Bloque 0) es lo PRIMERO del día.** Kevin (KAN-57) y Leonardo (KAN-58) no pueden empezar sin ella. Súbela en máximo 45 minutos y después sigue con tu guía de Java ([HU-NOT00](./GUIA-DYLAN-HU-NOT00-JAVA.md)) y con el Bloque 1 de esta.

## Estructura final del microservicio y quién es dueño de cada parte

```
notifications-service/src/
├── main.ts · app.module.ts                 ← BASE (Dylan). app.module.ts NO se vuelve a tocar.
├── config/env.validation.ts                ← BASE (Dylan)
├── domain/                                 ← tipos y reglas puras (sin Nest)
│   ├── notification-event.ts · notification.ts · delivery-status.ts   ← BASE (Dylan)
│   ├── notification-policy.ts              ← Kevin
│   └── delivery-status-transition.ts       ← Leonardo
├── application/
│   ├── ports/ (email-sender, template-renderer, notification-log)     ← BASE (Dylan)
│   ├── ports/idempotency.port.ts · handle-notification-event.use-case.ts ← Kevin
│   └── process-delivery-event.use-case.ts  ← Leonardo
├── infrastructure/
│   ├── email/        (Resend, log)         ← Dylan
│   ├── messaging/    (RabbitMQ, Redis, métricas)  ← Kevin
│   ├── persistence/  (TypeORM, NotificationLog)   ← Leonardo (la BASE trae una versión en memoria)
│   ├── webhooks/     (POST /webhooks/resend)       ← Leonardo
│   ├── observability/(GET /health)                 ← Leonardo (la BASE trae /health/live)
│   └── http/swagger.ts                             ← Leonardo
├── templates/        (Handlebars)          ← Dylan
├── shared/ · test-support/                 ← BASE (Dylan)
```

> **Regla de oro para no pisarse:** cada persona sólo edita **su** módulo (`*.module.ts`) y sus carpetas. `app.module.ts` ya importa los 6 módulos desde la base, así que nadie más lo modifica.

---

# BLOQUE 0: Base compartida (máx. 45 min)

## Paso 0: Ramas

```bash
cd ~/Projects/SkillBridge-AI-JAVA-Angular-Celula-1
git checkout develop && git pull origin develop

# Rama de integración de la épica (una sola vez, la crea Dylan)
git checkout -b feature/notificaciones
git push -u origin feature/notificaciones

# Rama para la base del microservicio
git checkout -b feature/notificaciones-nest-base
```

## Paso 1: Crear el proyecto e instalar TODAS las dependencias

```bash
# Proyecto NestJS 11 (CommonJS), TypeScript estricto, sin repo git propio
npx @nestjs/cli@11 new notifications-service --package-manager npm --skip-git --strict
cd notifications-service

# TODAS las dependencias del equipo se instalan AQUÍ, una sola vez.
# Así Kevin y Leonardo no tocan package.json y no hay conflictos en package-lock.json.
npm install @nestjs/config@^4 joi@^18 class-validator class-transformer \
  resend@^6 handlebars \
  @golevelup/nestjs-rabbitmq@^6 ioredis prom-client \
  @nestjs/typeorm@^11 typeorm@^0.3 pg @nestjs/terminus@^11 @nestjs/swagger@^11
npm install -D @types/amqplib

# Script de lint sin --fix (lo usa el CI)
npm pkg set scripts.lint:check='eslint "{src,test}/**/*.ts"'

# Borrar los ejemplos del generador
rm src/app.controller.ts src/app.service.ts src/app.controller.spec.ts test/app.e2e-spec.ts
echo "22" > .nvmrc
```

**Por qué esas versiones exactas (verificado el 2026-11-11):**
- El CLI 11 genera **NestJS 11 en CommonJS**, pero varias librerías ya publicaron versiones **sólo ESM** para Nest 12. Con esas versiones Jest falla con `Must use import to load ES Module`:
  - `@nestjs/config` **12.x** → la de Nest 11 es la **4.x**.
  - `@golevelup/nestjs-rabbitmq` **7+** (depende de `promise-breaker@7`, que es ESM) → la **6.x** es CommonJS y tiene la misma API.
  - `typeorm` **1.x** es muy reciente; se usa la **0.3** (estable y documentada).
- Con este conjunto, el código de las 3 guías **compila, pasa ESLint y pasa 70 pruebas**.

**Qué es cada librería:**

| Librería | Para qué |
|---|---|
| `@nestjs/config` y `joi` | Leer y validar variables de entorno |
| `class-validator` y `class-transformer` | Validar el JSON de los eventos (Kevin) |
| `resend` | SDK oficial de Resend: envío y verificación de webhooks |
| `handlebars` | Motor de plantillas (CA 1.3) |
| `@golevelup/nestjs-rabbitmq` | Consumir RabbitMQ (Kevin) |
| `ioredis` | Redis para idempotencia (Kevin) |
| `prom-client` | Métricas para Prometheus (Kevin) |
| `@nestjs/typeorm`, `typeorm` y `pg` | Base de datos del registro de envíos (Leonardo) |
| `@nestjs/terminus` | `/health` (Leonardo) |
| `@nestjs/swagger` | Documentación OpenAPI (Leonardo) |

📝 **Modificar** el `.gitignore` de la **raíz del repo**. Agrega al final:

```gitignore
# Notifications service (NestJS)
notifications-service/node_modules/
notifications-service/dist/
notifications-service/coverage/
```

## Paso 2: Configuración validada

📄 **Crear** `notifications-service/src/config/env.validation.ts`

```ts
import * as Joi from 'joi';

/**
 * Valida TODAS las variables de entorno al arrancar.
 * Si falta una obligatoria o tiene mal formato, el servicio no arranca y dice exactamente qué falla.
 */
export const envValidationSchema = Joi.object({
  NODE_ENV: Joi.string()
    .valid('development', 'production', 'test')
    .default('development'),
  PORT: Joi.number().port().default(3100),

  // Infraestructura
  RABBITMQ_URL: Joi.string()
    .uri({ scheme: ['amqp', 'amqps'] })
    .required(),
  RABBITMQ_PREFETCH: Joi.number().integer().min(1).default(5),
  REDIS_URL: Joi.string()
    .uri({ scheme: ['redis', 'rediss'] })
    .required(),
  IDEMPOTENCY_TTL_SECONDS: Joi.number().integer().min(60).default(604800),
  NOTIFICATIONS_DATABASE_URL: Joi.string()
    .uri({ scheme: ['postgres', 'postgresql'] })
    .required(),

  // Correo (Resend)
  EMAIL_DRIVER: Joi.string().valid('log', 'resend').default('log'),
  RESEND_API_KEY: Joi.when('EMAIL_DRIVER', {
    is: 'resend',
    then: Joi.string().pattern(/^re_/).required(),
    otherwise: Joi.string().allow('').default(''),
  }),
  RESEND_WEBHOOK_SECRET: Joi.string()
    .pattern(/^whsec_/)
    .allow('')
    .default(''),
  MAIL_FROM: Joi.string().default('SkillBridge AI <onboarding@resend.dev>'),
  MAIL_REPLY_TO: Joi.string().allow('').default(''),
  MAIL_REDIRECT_TO: Joi.string().allow('').default(''),
  FRONTEND_URL: Joi.string().uri().default('http://localhost:8088'),
});
```

**Explicación:**
- Al arrancar, Nest valida **todas** las variables con Joi. Si algo falta, el servicio **no arranca** y muestra el error exacto, por ejemplo `"REDIS_URL" is required`. Esto se llama *fail fast*.
- **`EMAIL_DRIVER`:** con `log` (por defecto) los correos no se envían, sólo se imprimen en el log. El CI y cualquier compañero sin API key pueden trabajar igual.
- **`Joi.when`:** la API key sólo es obligatoria con `EMAIL_DRIVER=resend`, y debe empezar por `re_`.
- `REDIS_URL`, `NOTIFICATIONS_DATABASE_URL` y `RESEND_WEBHOOK_SECRET` los usan Kevin y Leonardo. Se declaran aquí para que la validación sea **una sola**.

## Paso 3: Dominio (tipos puros, sin Nest)

📄 **Crear** `notifications-service/src/domain/notification-event.ts`

```ts
/** Contrato v1 — docs/notifications/CONTRACT.md */
export const NOTIFICATION_EVENT_TYPES = [
  'user.registered',
  'user.created',
  'user.status.changed',
  'user.role.changed',
  'user.deleted',
  'enrollment.created',
] as const;

export type NotificationEventType = (typeof NOTIFICATION_EVENT_TYPES)[number];
export type Role = 'CUSTOMER' | 'PROVIDER' | 'ADMIN';
export type AccountStatus = 'ACTIVE' | 'INACTIVE';

export interface EventActor {
  id: string | null;
  email: string;
  role: Role | null;
}

export interface EventSubject {
  id: string;
  name: string;
  email: string;
  role: Role;
  status: AccountStatus;
}

export interface AdminRecipient {
  email: string;
  name: string;
}

export interface NotificationEvent {
  eventId: string;
  eventType: NotificationEventType;
  version: number;
  occurredAt: string;
  actor: EventActor | null;
  subject: EventSubject;
  admins: AdminRecipient[];
  data: Record<string, string>;
}
```

📄 **Crear** `notifications-service/src/domain/notification.ts`

```ts
export const TEMPLATE_KEYS = [
  'welcome',
  'admin.new-registration',
  'account-created',
  'status-changed',
  'admin.status-changed',
  'role-changed',
  'account-deleted',
  'admin.account-deleted',
  'booking-confirmed',
  'provider.new-enrollment',
] as const;

export type TemplateKey = (typeof TEMPLATE_KEYS)[number];

/** "Hay que mandar ESTA plantilla a ESTA persona" (todavía sin HTML). */
export interface NotificationIntent {
  template: TemplateKey;
  to: string;
  recipientName: string;
  idempotencyKey: string;
}

export interface RenderedEmail {
  subject: string;
  html: string;
  text: string;
}

export interface EmailTag {
  name: string;
  value: string;
}

export interface OutgoingEmail extends RenderedEmail {
  to: string;
  idempotencyKey: string;
  tags: EmailTag[];
}
```

📄 **Crear** `notifications-service/src/domain/delivery-status.ts`

```ts
/** Estados de un correo en el registro de auditoría (NotificationLog). */
export const DELIVERY_STATUSES = [
  'sent',
  'delivery_delayed',
  'delivered',
  'bounced',
  'complained',
  'failed',
] as const;

export type DeliveryStatus = (typeof DELIVERY_STATUSES)[number];
```

**Explicación:**
- **`notification-event.ts`** es el contrato en TypeScript: la misma forma que el JSON de [CONTRACT.md](./CONTRACT.md). Incluye los 5 eventos de cuenta y `enrollment.created` (confirmación de reserva).
- `as const` + `(typeof X)[number]` genera a la vez un *union type* y un arreglo usable en runtime (Kevin lo usa para validar).
- **`notification.ts`** define las 10 plantillas (`TemplateKey`) y la "intención" de enviar (`NotificationIntent`). Así se separa **a quién y qué** (la política de Kevin) del **cómo se ve** (tus plantillas).
- **`delivery-status.ts`** son los estados del registro de auditoría que usa Leonardo.

## Paso 4: Puertos de aplicación (contratos entre los 3)

📄 **Crear** `notifications-service/src/application/ports/email-sender.port.ts`

```ts
import type { OutgoingEmail } from '../../domain/notification';

export interface SentEmail {
  id: string;
}

/** Puerto de salida. Implementaciones: LogEmailAdapter (dev/test) y ResendEmailAdapter. */
export abstract class EmailSenderPort {
  abstract send(email: OutgoingEmail): Promise<SentEmail>;
}

/** Error transitorio (red, 429, 5xx): vale la pena reintentar. */
export class RetryableEmailError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'RetryableEmailError';
  }
}

/** Error permanente (datos inválidos, cuota agotada): reintentar no sirve → DLQ. */
export class PermanentEmailError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'PermanentEmailError';
  }
}
```

📄 **Crear** `notifications-service/src/application/ports/template-renderer.port.ts`

```ts
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
```

📄 **Crear** `notifications-service/src/application/ports/notification-log.port.ts`

```ts
import type { DeliveryStatus } from '../../domain/delivery-status';

export interface SentNotification {
  eventId: string;
  eventType: string;
  template: string;
  recipient: string;
  providerMessageId: string;
}

export interface FailedNotification {
  eventId: string;
  eventType: string;
  template: string;
  recipient: string;
  error: string;
}

/** Puerto de salida: registro de auditoría de envíos (NotificationLog). */
export abstract class NotificationLogPort {
  abstract recordSent(entry: SentNotification): Promise<void>;
  abstract recordFailed(entry: FailedNotification): Promise<void>;
  abstract findStatus(
    providerMessageId: string,
  ): Promise<DeliveryStatus | null>;
  abstract updateDeliveryStatus(
    providerMessageId: string,
    status: DeliveryStatus,
    occurredAt: Date,
  ): Promise<void>;
}
```

**Explicación:**
- Son **clases abstractas** y no `interface`, porque Nest necesita un *token* que exista en runtime para inyectar dependencias (las interfaces desaparecen al compilar).
- **`EmailSenderPort`** lo implementas tú (Resend). `RetryableEmailError` y `PermanentEmailError` le dicen a Kevin si debe reintentar o mandar a la DLQ.
- **`TemplateRendererPort`** lo implementas tú (Handlebars).
- **`NotificationLogPort`** lo implementa Leonardo (PostgreSQL). Kevin lo llama al enviar y Leonardo lo actualiza con los webhooks.

## Paso 5: Adaptadores y módulos BASE (stubs para que todos arranquen)

📄 **Crear** `notifications-service/src/shared/mask-email.ts`

```ts
/** "ana.gomez@gmail.com" → "a***@gmail.com" (para logs sin datos personales). */
export function maskEmail(email: string): string {
  const [user, domain] = email.split('@');
  if (!user || !domain) return '***';
  return `${user[0]}***@${domain}`;
}
```

📄 **Crear** `notifications-service/src/test-support/load-fixture.ts`

```ts
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/** Ruta a docs/notifications/contract desde notifications-service/src/test-support */
export const CONTRACT_DIR = join(
  __dirname,
  '..',
  '..',
  '..',
  'docs',
  'notifications',
  'contract',
);

export const FIXTURE_NAMES = [
  'user.registered',
  'user.created',
  'user.status.changed',
  'user.role.changed',
  'user.deleted',
  'enrollment.created',
] as const;

export type FixtureName = (typeof FIXTURE_NAMES)[number];

export function loadFixture(name: FixtureName): unknown {
  return JSON.parse(
    readFileSync(join(CONTRACT_DIR, `${name}.json`), 'utf8'),
  ) as unknown;
}
```

📄 **Crear** `notifications-service/src/infrastructure/email/resend.module.ts`

```ts
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
```

📄 **Crear** `notifications-service/src/infrastructure/email/log-email.adapter.ts`

```ts
import { Injectable, Logger } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import type { OutgoingEmail } from '../../domain/notification';
import {
  EmailSenderPort,
  type SentEmail,
} from '../../application/ports/email-sender.port';
import { maskEmail } from '../../shared/mask-email';

/**
 * EMAIL_DRIVER=log → no envía nada, sólo registra. Útil en desarrollo, CI y pruebas.
 * Guarda los correos en memoria (`sent`) para inspeccionarlos en tests.
 */
@Injectable()
export class LogEmailAdapter implements EmailSenderPort {
  private readonly logger = new Logger(LogEmailAdapter.name);
  readonly sent: OutgoingEmail[] = [];

  send(email: OutgoingEmail): Promise<SentEmail> {
    this.sent.push(email);
    this.logger.log(
      `[EMAIL_DRIVER=log] to=${maskEmail(email.to)} subject="${email.subject}" key=${email.idempotencyKey}`,
    );
    return Promise.resolve({ id: `log-${randomUUID()}` });
  }
}
```

📄 **Crear** `notifications-service/src/infrastructure/email/email.module.ts` (versión BASE)

```ts
import { Module } from '@nestjs/common';
import { EmailSenderPort } from '../../application/ports/email-sender.port';
import { LogEmailAdapter } from './log-email.adapter';
import { ResendModule } from './resend.module';

/** BASE: sólo driver "log". En el Bloque 1 Dylan agrega el driver "resend". */
@Module({
  imports: [ResendModule],
  providers: [{ provide: EmailSenderPort, useClass: LogEmailAdapter }],
  exports: [EmailSenderPort],
})
export class EmailModule {}
```

📄 **Crear** `notifications-service/src/templates/basic-template.renderer.ts`

```ts
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
```

📄 **Crear** `notifications-service/src/templates/templates.module.ts` (versión BASE)

```ts
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
```

📄 **Crear** `notifications-service/src/infrastructure/persistence/in-memory-notification-log.adapter.ts`

```ts
import { Injectable } from '@nestjs/common';
import type { DeliveryStatus } from '../../domain/delivery-status';
import {
  NotificationLogPort,
  type FailedNotification,
  type SentNotification,
} from '../../application/ports/notification-log.port';

export interface NotificationLogRecord {
  eventId: string;
  eventType: string;
  template: string;
  recipient: string;
  providerMessageId: string | null;
  status: DeliveryStatus;
  error: string | null;
  lastEventAt: Date;
}

/** Implementación en memoria: base del proyecto y pruebas. Leonardo la reemplaza por TypeORM. */
@Injectable()
export class InMemoryNotificationLogAdapter implements NotificationLogPort {
  readonly records: NotificationLogRecord[] = [];

  recordSent(entry: SentNotification): Promise<void> {
    this.records.push({
      ...entry,
      status: 'sent',
      error: null,
      lastEventAt: new Date(),
    });
    return Promise.resolve();
  }

  recordFailed(entry: FailedNotification): Promise<void> {
    this.records.push({
      ...entry,
      providerMessageId: null,
      status: 'failed',
      lastEventAt: new Date(),
    });
    return Promise.resolve();
  }

  findStatus(providerMessageId: string): Promise<DeliveryStatus | null> {
    const record = this.records.find(
      (r) => r.providerMessageId === providerMessageId,
    );
    return Promise.resolve(record ? record.status : null);
  }

  updateDeliveryStatus(
    providerMessageId: string,
    status: DeliveryStatus,
    occurredAt: Date,
  ): Promise<void> {
    const record = this.records.find(
      (r) => r.providerMessageId === providerMessageId,
    );
    if (record) {
      record.status = status;
      record.lastEventAt = occurredAt;
    }
    return Promise.resolve();
  }
}
```

📄 **Crear** `notifications-service/src/infrastructure/persistence/persistence.module.ts` (versión BASE)

```ts
import { Module } from '@nestjs/common';
import { NotificationLogPort } from '../../application/ports/notification-log.port';
import { InMemoryNotificationLogAdapter } from './in-memory-notification-log.adapter';

/** BASE: registro en memoria. Leonardo (KAN-58) lo cambia por PostgreSQL + TypeORM. */
@Module({
  providers: [
    { provide: NotificationLogPort, useClass: InMemoryNotificationLogAdapter },
  ],
  exports: [NotificationLogPort],
})
export class PersistenceModule {}
```

📄 **Crear** `notifications-service/src/infrastructure/messaging/messaging.module.ts` (versión BASE)

```ts
import { Module } from '@nestjs/common';

/** BASE: vacío. Kevin (KAN-57) agrega RabbitMQ, Redis, idempotencia y el consumidor. */
@Module({})
export class MessagingModule {}
```

📄 **Crear** `notifications-service/src/infrastructure/observability/liveness.controller.ts`

```ts
import { Controller, Get } from '@nestjs/common';

/** Liveness mínimo para el healthcheck de Docker: "el proceso responde". */
@Controller('health')
export class LivenessController {
  @Get('live')
  live(): { status: string } {
    return { status: 'ok' };
  }
}
```

📄 **Crear** `notifications-service/src/infrastructure/observability/observability.module.ts` (versión BASE)

```ts
import { Module } from '@nestjs/common';
import { LivenessController } from './liveness.controller';

/** BASE: sólo /health/live. Leonardo (KAN-58) agrega /health completo con Terminus. */
@Module({
  controllers: [LivenessController],
})
export class ObservabilityModule {}
```

📄 **Crear** `notifications-service/src/infrastructure/webhooks/webhooks.module.ts` (versión BASE)

```ts
import { Module } from '@nestjs/common';

/** BASE: vacío. Leonardo (KAN-58) agrega POST /webhooks/resend. */
@Module({})
export class WebhooksModule {}
```

**Explicación:**
- **`ResendModule` (CA 1.2)** es `@Global()` y expone el cliente con el token `RESEND_CLIENT`. Lo usan tres partes: tu adaptador de envío, el verificador de webhooks de Leonardo y su health check. El SDK de Resend **lanza un error si se crea sin llave**; con `EMAIL_DRIVER=log` se usa un valor de relleno, que es seguro porque nunca se envía nada.
- **`LogEmailAdapter`** es un "falso" que guarda los correos en memoria (`sent`). Lo usan el CI, las pruebas y quien no tenga API key.
- **`BasicTemplateRenderer`** e **`InMemoryNotificationLogAdapter`** son implementaciones mínimas: con ellas el servicio arranca **hoy**, y cada persona las reemplaza en **su** módulo.
- **`MessagingModule` y `WebhooksModule` vacíos:** existen para que `app.module.ts` ya los importe y nadie tenga que editarlo después.
- **`/health/live`** sólo responde "estoy vivo". Lo usa el healthcheck de Docker. El `/health` completo es de Leonardo.
- **`load-fixture.ts`** lee los JSON de `docs/notifications/contract/`. Las pruebas de Java y de Nest usan **los mismos** ejemplos.

## Paso 6: `app.module.ts`, `main.ts` y `.env.example`

📝 **Reemplazar** `notifications-service/src/app.module.ts`

```ts
import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { envValidationSchema } from './config/env.validation';
import { EmailModule } from './infrastructure/email/email.module';
import { MessagingModule } from './infrastructure/messaging/messaging.module';
import { ObservabilityModule } from './infrastructure/observability/observability.module';
import { PersistenceModule } from './infrastructure/persistence/persistence.module';
import { WebhooksModule } from './infrastructure/webhooks/webhooks.module';
import { TemplatesModule } from './templates/templates.module';

/**
 * Este archivo NO se vuelve a modificar después de la base.
 * Cada integrante trabaja SOLO dentro de su módulo:
 *  - Dylan:    EmailModule, TemplatesModule
 *  - Kevin:    MessagingModule
 *  - Leonardo: PersistenceModule, ObservabilityModule, WebhooksModule
 */
@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      validationSchema: envValidationSchema,
    }),
    EmailModule,
    TemplatesModule,
    PersistenceModule,
    MessagingModule,
    ObservabilityModule,
    WebhooksModule,
  ],
})
export class AppModule {}
```

📄 **Crear** `notifications-service/src/main.ts` (versión BASE)

```ts
import { Logger, ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';

async function bootstrap(): Promise<void> {
  // rawBody: true → guarda el cuerpo original de cada petición (necesario para verificar la firma de los webhooks de Resend)
  const app = await NestFactory.create(AppModule, { rawBody: true });
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, transform: true }));
  app.enableShutdownHooks();
  const port = Number(app.get(ConfigService).get('PORT', 3100));
  await app.listen(port);
  Logger.log(
    `notifications-service escuchando en el puerto ${port}`,
    'Bootstrap',
  );
}

void bootstrap();
```

📄 **Crear** `notifications-service/.env.example`

```dotenv
# Copia este archivo a .env para correr el servicio fuera de Docker (npm run start:dev)
PORT=3100
RABBITMQ_URL=amqp://guest:guest@localhost:5672
REDIS_URL=redis://localhost:6379/1
NOTIFICATIONS_DATABASE_URL=postgresql://skillbridge:skillbridge_dev@localhost:5435/notifications
EMAIL_DRIVER=log
RESEND_API_KEY=
RESEND_WEBHOOK_SECRET=
MAIL_FROM=SkillBridge AI <onboarding@resend.dev>
MAIL_REPLY_TO=
MAIL_REDIRECT_TO=
FRONTEND_URL=http://localhost:8088
```

**Explicación:**
- **`app.module.ts`** importa los 6 módulos. El comentario deja claro quién es dueño de cada uno. **Nadie vuelve a editar este archivo.**
- **`rawBody: true`:** Nest guarda el cuerpo HTTP original. Leonardo lo necesita para verificar la firma de los webhooks: si se usara el JSON ya parseado, la firma no coincidiría.
- **`ValidationPipe` global:** valida automáticamente los DTO de cualquier endpoint HTTP (CA 2.4).
- **`enableShutdownHooks()`:** al recibir SIGTERM de Docker, cierra ordenadamente RabbitMQ, Redis y la base de datos.

## Paso 7: Verificar y subir la base

```bash
cd notifications-service
npm run format          # Prettier: formato uniforme (ejecútalo SIEMPRE después de pegar código)
npm run build
npm run lint:check
npm test -- --passWithNoTests

cd ..
git add .gitignore notifications-service
git status              # NO debe aparecer .env ni node_modules
git commit -m "feat(notificaciones): base de notifications-service (NestJS 11, puertos, ResendModule y stubs)"
git push -u origin feature/notificaciones-nest-base
gh pr create --base feature/notificaciones --head feature/notificaciones-nest-base \
  --title "feat(notificaciones): base de notifications-service (KAN-56)" \
  --body "Scaffold NestJS 11, validación de entorno, dominio, puertos, ResendModule, adaptadores base y módulos por responsable. Desbloquea KAN-57 y KAN-58."
```

> 📣 **Avisa en el grupo:** "Base lista en `feature/notificaciones`". Kevin y Leonardo hacen `git pull` y arrancan.

---

# BLOQUE 1: SDK de Resend y plantillas (tu parte de KAN-56)

```bash
git checkout feature/notificaciones && git pull
git checkout -b feature/notificaciones-resend-templates
```

## Paso 8 (CA 1.2): Adaptador de envío con Resend

### 8.1 Adaptador

📄 **Crear** `notifications-service/src/infrastructure/email/resend-email.adapter.ts`

```ts
import { Logger } from '@nestjs/common';
import type { Resend } from 'resend';
import type { OutgoingEmail } from '../../domain/notification';
import {
  EmailSenderPort,
  PermanentEmailError,
  RetryableEmailError,
  type SentEmail,
} from '../../application/ports/email-sender.port';
import { maskEmail } from '../../shared/mask-email';

/** Errores de Resend que vale la pena reintentar (https://resend.com/docs/api-reference/errors). */
const RETRYABLE_ERRORS = new Set<string>([
  'rate_limit_exceeded',
  'concurrent_idempotent_requests',
  'application_error',
  'internal_server_error',
]);

export interface ResendAdapterOptions {
  from: string;
  replyTo?: string;
  /** Sólo desarrollo: si se define, TODOS los correos van a esta dirección. */
  redirectTo?: string;
}

export type ResendClient = Pick<Resend, 'emails'>;

export class ResendEmailAdapter implements EmailSenderPort {
  private readonly logger = new Logger(ResendEmailAdapter.name);

  constructor(
    private readonly client: ResendClient,
    private readonly options: ResendAdapterOptions,
  ) {
    if (options.redirectTo) {
      this.logger.warn(
        `MAIL_REDIRECT_TO activo: todos los correos irán a ${maskEmail(options.redirectTo)}`,
      );
    }
  }

  async send(email: OutgoingEmail): Promise<SentEmail> {
    const redirect = this.options.redirectTo;
    const to = redirect ?? email.to;
    const subject = redirect
      ? `[para: ${email.to}] ${email.subject}`
      : email.subject;

    let response: Awaited<ReturnType<ResendClient['emails']['send']>>;
    try {
      response = await this.client.emails.send(
        {
          from: this.options.from,
          to: [to],
          subject,
          html: email.html,
          text: email.text,
          replyTo: this.options.replyTo,
          tags: email.tags,
        },
        { idempotencyKey: email.idempotencyKey },
      );
    } catch (error) {
      // Timeout, DNS o conexión rechazada: el problema es la red, no los datos.
      throw new RetryableEmailError(
        `Error de red con Resend: ${error instanceof Error ? error.message : String(error)}`,
      );
    }

    if (response.error) {
      const detail = `${response.error.name}: ${response.error.message}`;
      if (RETRYABLE_ERRORS.has(response.error.name)) {
        throw new RetryableEmailError(detail);
      }
      throw new PermanentEmailError(detail);
    }

    if (!response.data) {
      throw new RetryableEmailError('Resend respondió sin id de correo');
    }

    return { id: response.data.id };
  }
}
```

**Explicación:**
1. **El SDK no lanza excepciones ante errores de la API:** devuelve `{ data, error }`. Según `error.name` se decide:
   - `rate_limit_exceeded` (429) y `internal_server_error` (5xx) son **transitorios**: se lanza `RetryableEmailError` y Kevin reintenta con espera creciente.
   - `validation_error`, `invalid_from_address`, `daily_quota_exceeded`… son **permanentes**: se lanza `PermanentEmailError`, el mensaje va a la DLQ y Leonardo lo ve como `failed` en el log.
2. El `try/catch` cubre los fallos de **red** (sin internet, DNS), que también son transitorios.
3. **`idempotencyKey`:** si Resend recibe dos veces la misma llave en 24 h, **no** envía el segundo correo. Es la segunda capa de protección contra duplicados (la primera es Redis, de Kevin).
4. **`MAIL_REDIRECT_TO`:** en desarrollo, todo llega a tu bandeja y el asunto indica a quién iba dirigido (decisión aprobada por el equipo).
5. **`ResendClient = Pick<Resend, 'emails'>`:** el adaptador sólo depende de la parte del SDK que usa, así en las pruebas basta un objeto `{ emails: { send } }`.

### 8.2 Prueba con mock del cliente (CA 1.4)

📄 **Crear** `notifications-service/src/infrastructure/email/resend-email.adapter.spec.ts`

```ts
import {
  PermanentEmailError,
  RetryableEmailError,
} from '../../application/ports/email-sender.port';
import type { OutgoingEmail } from '../../domain/notification';
import { ResendEmailAdapter, type ResendClient } from './resend-email.adapter';

const email: OutgoingEmail = {
  to: 'delivered@resend.dev',
  subject: 'Hola',
  html: '<p>Hola</p>',
  text: 'Hola',
  idempotencyKey: 'evt-1:welcome:delivered@resend.dev',
  tags: [{ name: 'template', value: 'welcome' }],
};

function adapterWith(send: jest.Mock, redirectTo?: string): ResendEmailAdapter {
  const client = { emails: { send } } as unknown as ResendClient;
  return new ResendEmailAdapter(client, {
    from: 'SkillBridge <no-reply@test.dev>',
    redirectTo,
  });
}

describe('ResendEmailAdapter', () => {
  it('envía con la llave de idempotencia y devuelve el id', async () => {
    const send = jest
      .fn()
      .mockResolvedValue({ data: { id: 're_123' }, error: null });
    await expect(adapterWith(send).send(email)).resolves.toEqual({
      id: 're_123',
    });
    expect(send).toHaveBeenCalledWith(
      expect.objectContaining({
        to: ['delivered@resend.dev'],
        subject: 'Hola',
      }),
      { idempotencyKey: email.idempotencyKey },
    );
  });

  it('redirige todos los correos cuando MAIL_REDIRECT_TO está activo', async () => {
    const send = jest
      .fn()
      .mockResolvedValue({ data: { id: 're_1' }, error: null });
    await adapterWith(send, 'yo@equipo.dev').send(email);
    expect(send).toHaveBeenCalledWith(
      expect.objectContaining({
        to: ['yo@equipo.dev'],
        subject: '[para: delivered@resend.dev] Hola',
      }),
      expect.anything(),
    );
  });

  it('convierte rate_limit_exceeded en error reintentable', async () => {
    const send = jest.fn().mockResolvedValue({
      data: null,
      error: { name: 'rate_limit_exceeded', message: 'Too many requests' },
    });
    await expect(adapterWith(send).send(email)).rejects.toBeInstanceOf(
      RetryableEmailError,
    );
  });

  it('convierte validation_error en error permanente', async () => {
    const send = jest.fn().mockResolvedValue({
      data: null,
      error: { name: 'validation_error', message: 'Invalid `to` field' },
    });
    await expect(adapterWith(send).send(email)).rejects.toBeInstanceOf(
      PermanentEmailError,
    );
  });

  it('trata un fallo de red como reintentable', async () => {
    const send = jest.fn().mockRejectedValue(new Error('ECONNRESET'));
    await expect(adapterWith(send).send(email)).rejects.toBeInstanceOf(
      RetryableEmailError,
    );
  });
});
```

**Explicación:** no se llama a Resend real: el cliente es un `jest.fn()`. Se prueba lo que importa: que se envíe con la llave de idempotencia, que funcione la redirección y que los errores se clasifiquen bien (de eso depende reintentar o mandar a la DLQ).

### 8.3 Elegir el driver

📝 **Reemplazar** `notifications-service/src/infrastructure/email/email.module.ts`

```ts
import { Logger, Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { Resend } from 'resend';
import { EmailSenderPort } from '../../application/ports/email-sender.port';
import { LogEmailAdapter } from './log-email.adapter';
import { ResendEmailAdapter } from './resend-email.adapter';
import { RESEND_CLIENT, ResendModule } from './resend.module';

@Module({
  imports: [ResendModule],
  providers: [
    {
      provide: EmailSenderPort,
      inject: [ConfigService, RESEND_CLIENT],
      useFactory: (config: ConfigService, resend: Resend): EmailSenderPort => {
        const driver = config.get<string>('EMAIL_DRIVER', 'log');
        Logger.log(`Driver de correo: ${driver}`, 'EmailModule');
        if (driver !== 'resend') {
          return new LogEmailAdapter();
        }
        return new ResendEmailAdapter(resend, {
          from: config.getOrThrow<string>('MAIL_FROM'),
          replyTo: config.get<string>('MAIL_REPLY_TO') || undefined,
          redirectTo: config.get<string>('MAIL_REDIRECT_TO') || undefined,
        });
      },
    },
  ],
  exports: [EmailSenderPort],
})
export class EmailModule {}
```

**Explicación:** `useFactory` decide **al arrancar** entre `log` y `resend`, y recibe el cliente inyectado desde `ResendModule` (`RESEND_CLIENT`). El resto del sistema sólo conoce `EmailSenderPort`, así que no cambia nada más.

## Paso 9 (CA 1.3): Motor de plantillas Handlebars

**Diseño:**
- **El texto de los correos vive en archivos `.hbs`.** Se pueden editar sin saber TypeScript.
- **Los datos se preparan en TypeScript** (`template-context.ts`): etiquetas en español, fechas formateadas y URLs.
- **Un layout base** (`layout.hbs`) más **2 parciales reutilizables** (`button` y `details`).
- **Handlebars escapa automáticamente** todo lo que va entre `{{ }}`. Si un usuario se llama `<script>`, se muestra como texto y no se ejecuta.
- **Modo `strict`:** si una plantilla usa una variable que no existe (por ejemplo, un error de tipeo como `{{fistName}}`), el render **falla** en vez de dejar el hueco vacío, y las pruebas lo detectan.

### 9.1 Contexto (datos para las plantillas)

📄 **Crear** `notifications-service/src/templates/template-context.ts`

```ts
import type { NotificationEvent } from '../domain/notification-event';

const ROLE_LABELS: Record<string, string> = {
  CUSTOMER: 'Cliente',
  PROVIDER: 'Proveedor',
  ADMIN: 'Administrador',
};

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Activa',
  INACTIVE: 'Inactiva',
};

const ROLE_HINTS: Record<string, string> = {
  PROVIDER:
    'Ahora puedes publicar y gestionar tus propios servicios desde el panel de proveedor.',
  CUSTOMER: 'Puedes explorar los servicios disponibles e inscribirte en ellos.',
  ADMIN: 'Ahora tienes acceso al panel de administración de la plataforma.',
};

const DATE_TIME = new Intl.DateTimeFormat('es-CO', {
  dateStyle: 'long',
  timeStyle: 'short',
  timeZone: 'America/Bogota',
});

/** Las fechas sin hora (2026-11-20) se formatean en UTC para que no "retrocedan" un día. */
const DATE_ONLY = new Intl.DateTimeFormat('es-CO', {
  dateStyle: 'long',
  timeZone: 'UTC',
});

const labelOf = (map: Record<string, string>, key?: string): string =>
  key ? (map[key] ?? key) : '—';

const firstName = (fullName: string): string =>
  fullName.trim().split(/\s+/)[0] || fullName;

function formatDateTime(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : DATE_TIME.format(date);
}

function formatDay(isoDay?: string): string {
  if (!isoDay) return '—';
  const date = new Date(`${isoDay}T00:00:00Z`);
  return Number.isNaN(date.getTime()) ? isoDay : DATE_ONLY.format(date);
}

export interface DetailRow {
  label: string;
  value: string;
}

/**
 * Convierte el evento en las variables que usan las plantillas .hbs.
 * Un solo contexto "plano" para todas las plantillas: cada .hbs usa sólo las que necesita.
 */
export function buildTemplateContext(
  event: NotificationEvent,
  recipientName: string,
  frontendUrl: string,
): Record<string, unknown> {
  const { subject, data, actor } = event;
  const isSelf =
    !!actor && actor.email.toLowerCase() === subject.email.toLowerCase();
  const actorLabel = !actor
    ? 'Tú (registro)'
    : isSelf
      ? 'Tú'
      : actor.role === 'ADMIN'
        ? 'Un administrador'
        : actor.email;
  const occurredAtLabel = formatDateTime(event.occurredAt);

  const subjectDetails: DetailRow[] = [
    { label: 'Nombre', value: subject.name },
    { label: 'Correo', value: subject.email },
    { label: 'Rol', value: labelOf(ROLE_LABELS, subject.role) },
    { label: 'Fecha', value: occurredAtLabel },
  ];

  const bookingDetails: DetailRow[] = [
    { label: 'Servicio', value: data.serviceName ?? '—' },
    { label: 'Inicio', value: formatDay(data.startDate) },
    { label: 'Hora', value: data.startTime || '—' },
    { label: 'Sesiones', value: data.sessions ?? '—' },
    { label: 'Finaliza', value: formatDay(data.endDate) },
    { label: 'Proveedor', value: data.providerName || '—' },
  ];

  return {
    // Personas
    firstName: firstName(subject.name),
    subjectName: subject.name,
    subjectEmail: subject.email,
    recipientFirstName: firstName(recipientName),
    actorLabel,
    actorLabelLower: actorLabel.toLowerCase(),
    changedByAdmin: actor?.role === 'ADMIN' && !isSelf,
    // Rol / estado
    roleLabel: labelOf(ROLE_LABELS, subject.role),
    previousRoleLabel: labelOf(ROLE_LABELS, data.previousRole),
    newRoleLabel: labelOf(ROLE_LABELS, data.newRole),
    roleHint: ROLE_HINTS[data.newRole ?? ''] ?? '',
    previousStatusLabel: labelOf(STATUS_LABELS, data.previousStatus),
    newStatusLabel: labelOf(STATUS_LABELS, data.newStatus),
    deactivated: data.newStatus === 'INACTIVE',
    // Reserva
    serviceName: data.serviceName ?? '',
    startDateLabel: formatDay(data.startDate),
    // Tablas
    subjectDetails,
    bookingDetails,
    changeDetails: [
      { label: 'Realizado por', value: actorLabel },
      { label: 'Fecha', value: occurredAtLabel },
    ] satisfies DetailRow[],
    statusDetails: [
      ...subjectDetails,
      {
        label: 'Estado anterior',
        value: labelOf(STATUS_LABELS, data.previousStatus),
      },
      { label: 'Estado nuevo', value: labelOf(STATUS_LABELS, data.newStatus) },
    ] satisfies DetailRow[],
    // Enlaces (rutas reales de frontend/src/app/app.routes.ts)
    frontendUrl,
    loginUrl: `${frontendUrl}/login`,
    adminUrl: `${frontendUrl}/admin`,
    bookingsUrl: `${frontendUrl}/bookings`,
    providerServicesUrl: `${frontendUrl}/provider/services`,
    roleUrl:
      data.newRole === 'PROVIDER'
        ? `${frontendUrl}/provider`
        : data.newRole === 'ADMIN'
          ? `${frontendUrl}/admin`
          : frontendUrl,
  };
}
```

**Explicación:**
- Un **solo contexto plano** para todas las plantillas: cada `.hbs` usa sólo las variables que necesita. Es más simple que mantener 10 funciones distintas.
- **`formatDay` usa `timeZone: 'UTC'`:** `2026-11-20` interpretado en hora de Colombia se convertiría en el 19 de noviembre. Error clásico, evitado.
- Las URLs apuntan a **rutas reales** de Angular (`/login`, `/admin`, `/bookings`, `/provider/services`, ver `frontend/src/app/app.routes.ts`).
- `satisfies DetailRow[]` comprueba el tipo sin perder la inferencia.

### 9.2 Catálogo: asunto y preheader de cada plantilla

📄 **Crear** `notifications-service/src/templates/template-catalog.ts`

```ts
import type { TemplateKey } from '../domain/notification';

/**
 * Asunto y "preheader" (texto gris que Gmail muestra junto al asunto) de cada plantilla.
 * Son plantillas Handlebars de una línea: usan las mismas variables que los .hbs.
 */
export const TEMPLATE_CATALOG: Record<
  TemplateKey,
  { view: string; subject: string; preheader: string }
> = {
  welcome: {
    view: 'welcome',
    subject: '¡Bienvenido/a a SkillBridge AI, {{firstName}}!',
    preheader:
      'Tu cuenta está lista. Explora mentorías y servicios profesionales.',
  },
  'admin.new-registration': {
    view: 'admin-new-registration',
    subject: 'Nuevo usuario registrado: {{subjectName}}',
    preheader: '{{subjectName}} acaba de crear una cuenta.',
  },
  'account-created': {
    view: 'account-created',
    subject: 'Un administrador creó tu cuenta en SkillBridge AI',
    preheader: 'Tu cuenta con rol {{roleLabel}} está lista.',
  },
  'status-changed': {
    view: 'status-changed',
    subject:
      '{{#if deactivated}}Tu cuenta de SkillBridge AI fue desactivada{{else}}Tu cuenta de SkillBridge AI fue reactivada{{/if}}',
    preheader:
      '{{#if deactivated}}Tu cuenta ya no está activa.{{else}}Ya puedes volver a usar la plataforma.{{/if}}',
  },
  'admin.status-changed': {
    view: 'admin-status-changed',
    subject:
      '{{subjectName}} {{#if deactivated}}desactivó{{else}}reactivó{{/if}} su cuenta',
    preheader: 'Cambio de estado de cuenta realizado por el propio usuario.',
  },
  'role-changed': {
    view: 'role-changed',
    subject: 'Tu rol en SkillBridge AI cambió a {{newRoleLabel}}',
    preheader: 'Nuevo rol: {{newRoleLabel}}.',
  },
  'account-deleted': {
    view: 'account-deleted',
    subject: 'Tu cuenta de SkillBridge AI fue eliminada',
    preheader: 'Confirmación de eliminación de cuenta.',
  },
  'admin.account-deleted': {
    view: 'admin-account-deleted',
    subject: '{{subjectName}} eliminó su cuenta',
    preheader: 'Un usuario eliminó su cuenta.',
  },
  'booking-confirmed': {
    view: 'booking-confirmed',
    subject: 'Reserva confirmada: {{serviceName}}',
    preheader: 'Tu inscripción está lista. Inicia el {{startDateLabel}}.',
  },
  'provider.new-enrollment': {
    view: 'provider-new-enrollment',
    subject: 'Nuevo inscrito en {{serviceName}}: {{subjectName}}',
    preheader: '{{subjectName}} se inscribió en tu servicio.',
  },
};
```

**Explicación:** `Record<TemplateKey, …>` **obliga** a definir las 10 plantillas: si falta una, TypeScript no compila. El asunto también es Handlebars (`{{firstName}}`).

### 9.3 Texto plano a partir del HTML

📄 **Crear** `notifications-service/src/templates/html-to-text.ts`

```ts
const ENTITIES: Record<string, string> = {
  '&amp;': '&',
  '&lt;': '<',
  '&gt;': '>',
  '&quot;': '"',
  '&#x27;': "'",
  '&#39;': "'",
  '&#x3D;': '=',
  '&#x60;': '`',
  '&nbsp;': ' ',
};

/** Versión texto plano del cuerpo HTML (para clientes sin HTML y filtros antispam). */
export function htmlToText(html: string): string {
  return html
    .replace(/<a [^>]*href="([^"]*)"[^>]*>(.*?)<\/a>/gi, '$2: $1')
    .replace(/<\/(p|h1|h2|tr|table)>|<br\s*\/?>/gi, '\n')
    .replace(/<\/td>/gi, ' ')
    .replace(/<[^>]+>/g, '')
    .replace(/&[#a-z0-9]+;/gi, (entity) => ENTITIES[entity] ?? entity)
    .split('\n')
    .map((line) => line.trim())
    .filter((line, i, lines) => line !== '' || (i > 0 && lines[i - 1] !== ''))
    .join('\n')
    .trim();
}
```

**Explicación:** Gmail, Outlook y los filtros antispam valoran que exista una versión en texto plano. Se genera a partir del mismo HTML, y los enlaces quedan como `Texto: URL`.

### 9.4 Plantillas `.hbs`

> **Importante:** van en `src/templates/views/`. En el Paso 10 se configura Nest para copiarlas a `dist/`.

📄 **Crear** `notifications-service/src/templates/views/layout.hbs`

```handlebars
<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{{subject}}</title>
</head>
<body style="margin:0;padding:0;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;">
<span style="display:none;max-height:0;overflow:hidden;opacity:0;">{{preheader}}</span>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f4f5f7;padding:24px 12px;">
<tr><td align="center">
<table role="presentation" width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%;background:#ffffff;border-radius:8px;overflow:hidden;">
<tr><td style="background:#4f46e5;padding:20px 32px;color:#ffffff;font-size:20px;font-weight:bold;">SkillBridge AI</td></tr>
<tr><td style="padding:32px;color:#1f2937;font-size:15px;line-height:1.6;">
{{{body}}}
</td></tr>
<tr><td style="padding:16px 32px;background:#f9fafb;font-size:12px;line-height:1.5;color:#6b7280;">
Recibes este correo porque tienes una cuenta en SkillBridge AI. Es un mensaje automático sobre tu cuenta y tus reservas.
</td></tr>
</table>
</td></tr>
</table>
</body>
</html>
```

📄 **Crear** `notifications-service/src/templates/views/partials/button.hbs`

```handlebars
<p style="margin:24px 0 8px;"><a href="{{url}}" style="display:inline-block;background:#4f46e5;color:#ffffff;text-decoration:none;padding:12px 22px;border-radius:6px;font-size:15px;font-weight:bold;">{{label}}</a></p>
```

📄 **Crear** `notifications-service/src/templates/views/partials/details.hbs`

```handlebars
<table role="presentation" cellpadding="0" cellspacing="0" style="width:100%;margin:8px 0 20px;border:1px solid #e5e7eb;border-radius:6px;">
{{#each rows}}
<tr><td style="padding:8px 12px;font-size:13px;color:#6b7280;width:40%;">{{this.label}}</td><td style="padding:8px 12px;font-size:13px;color:#111827;font-weight:bold;">{{this.value}}</td></tr>
{{/each}}
</table>
```

📄 **Crear** `notifications-service/src/templates/views/welcome.hbs`

```handlebars
<h1 style="margin:0 0 18px;font-size:22px;">Hola, {{firstName}} 👋</h1>
<p>Tu cuenta en SkillBridge AI se creó correctamente. Ya puedes explorar servicios y mentorías, inscribirte y agendar tus sesiones.</p>
<p>Si no fuiste tú quien creó esta cuenta, contacta al administrador de la plataforma.</p>
{{> button url=frontendUrl label="Explorar servicios"}}
```

📄 **Crear** `notifications-service/src/templates/views/admin-new-registration.hbs`

```handlebars
<h1 style="margin:0 0 18px;font-size:22px;">Se registró un nuevo usuario</h1>
<p>Hola {{recipientFirstName}}, un nuevo usuario se registró en la plataforma.</p>
{{> details rows=subjectDetails}}
{{> button url=adminUrl label="Ir al panel de administración"}}
```

📄 **Crear** `notifications-service/src/templates/views/account-created.hbs`

```handlebars
<h1 style="margin:0 0 18px;font-size:22px;">Hola, {{firstName}}</h1>
<p>Un administrador creó una cuenta para ti en SkillBridge AI con el rol de <strong>{{roleLabel}}</strong>.</p>
<p>Por seguridad, este correo no incluye contraseñas. El administrador te compartirá tus credenciales por un canal seguro; te recomendamos cambiarlas en tu primer ingreso.</p>
{{> button url=loginUrl label="Iniciar sesión"}}
```

📄 **Crear** `notifications-service/src/templates/views/status-changed.hbs`

```handlebars
{{#if deactivated}}
<h1 style="margin:0 0 18px;font-size:22px;">Tu cuenta fue desactivada</h1>
<p>Hola {{firstName}}, el estado de tu cuenta cambió de {{previousStatusLabel}} a {{newStatusLabel}}.</p>
<p>Mientras tu cuenta esté inactiva no podrás ingresar ni usar los servicios de la plataforma. Si crees que se trata de un error, contacta al administrador.</p>
{{else}}
<h1 style="margin:0 0 18px;font-size:22px;">¡Tu cuenta está activa de nuevo!</h1>
<p>Hola {{firstName}}, el estado de tu cuenta cambió de {{previousStatusLabel}} a {{newStatusLabel}}.</p>
<p>Ya puedes ingresar y usar la plataforma con normalidad.</p>
{{/if}}
{{> details rows=changeDetails}}
{{#unless deactivated}}
{{> button url=loginUrl label="Iniciar sesión"}}
{{/unless}}
```

📄 **Crear** `notifications-service/src/templates/views/admin-status-changed.hbs`

```handlebars
<h1 style="margin:0 0 18px;font-size:22px;">Cambio de estado de una cuenta</h1>
<p>Hola {{recipientFirstName}}, {{subjectName}} cambió el estado de su cuenta.</p>
{{> details rows=statusDetails}}
{{> button url=adminUrl label="Revisar en el panel"}}
```

📄 **Crear** `notifications-service/src/templates/views/role-changed.hbs`

```handlebars
<h1 style="margin:0 0 18px;font-size:22px;">Tu rol fue actualizado</h1>
<p>Hola {{firstName}}, {{actorLabelLower}} actualizó tu rol de {{previousRoleLabel}} a <strong>{{newRoleLabel}}</strong>.</p>
{{#if roleHint}}<p>{{roleHint}}</p>{{/if}}
{{> details rows=changeDetails}}
{{> button url=roleUrl label="Ir a SkillBridge AI"}}
```

📄 **Crear** `notifications-service/src/templates/views/account-deleted.hbs`

```handlebars
<h1 style="margin:0 0 18px;font-size:22px;">Tu cuenta fue eliminada</h1>
<p>Hola {{firstName}}, te confirmamos que la cuenta asociada a {{subjectEmail}} fue eliminada{{#if changedByAdmin}} por un administrador{{/if}}.</p>
<p>Tus credenciales ya no son válidas. Si no reconoces esta acción, contacta al administrador de inmediato.</p>
{{> details rows=changeDetails}}
```

📄 **Crear** `notifications-service/src/templates/views/admin-account-deleted.hbs`

```handlebars
<h1 style="margin:0 0 18px;font-size:22px;">Un usuario eliminó su cuenta</h1>
<p>Hola {{recipientFirstName}}, {{subjectName}} eliminó su cuenta de la plataforma.</p>
{{> details rows=subjectDetails}}
{{> button url=adminUrl label="Ir al panel de administración"}}
```

📄 **Crear** `notifications-service/src/templates/views/booking-confirmed.hbs`

```handlebars
<h1 style="margin:0 0 18px;font-size:22px;">¡Tu reserva está confirmada! 🎉</h1>
<p>Hola {{firstName}}, quedaste inscrito/a en <strong>{{serviceName}}</strong>. Estos son los detalles:</p>
{{> details rows=bookingDetails}}
<p>Te recomendamos llegar unos minutos antes a cada sesión. Si necesitas retirarte, hazlo desde "Mis reservas": dentro de las primeras 24 horas aplica reembolso.</p>
{{> button url=bookingsUrl label="Ver mis reservas"}}
```

📄 **Crear** `notifications-service/src/templates/views/provider-new-enrollment.hbs`

```handlebars
<h1 style="margin:0 0 18px;font-size:22px;">Tienes un nuevo inscrito</h1>
<p>Hola {{recipientFirstName}}, <strong>{{subjectName}}</strong> se inscribió en tu servicio <strong>{{serviceName}}</strong>.</p>
{{> details rows=bookingDetails}}
{{> button url=providerServicesUrl label="Ver mis servicios"}}
```

**Explicación de la sintaxis Handlebars usada:**

| Sintaxis | Qué hace |
|---|---|
| `{{firstName}}` | Inserta la variable **escapada** (segura contra inyección de HTML) |
| `{{{body}}}` | Inserta HTML **sin escapar**. Sólo se usa en el layout, con contenido que ya generó Handlebars |
| `{{> button url=loginUrl label="Iniciar sesión"}}` | Usa el parcial `button` con esos parámetros |
| `{{#if deactivated}} … {{else}} … {{/if}}` | Condicional |
| `{{#each rows}} {{this.label}} {{/each}}` | Recorre una lista (parcial `details`) |

- **HTML con tablas y estilos *inline*:** Gmail y Outlook ignoran `<style>` y el CSS moderno. Es la técnica estándar para correos.
- **`booking-confirmed.hbs`** es la **confirmación de reserva** (CA 1.3). Menciona la política real del backend: reembolso si te retiras dentro de las primeras 24 h (`ServiceEnrollmentService.withdraw`).

### 9.5 El renderer

📄 **Crear** `notifications-service/src/templates/handlebars-template.renderer.ts`

```ts
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
```

📝 **Reemplazar** `notifications-service/src/templates/templates.module.ts`

```ts
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
```

**Explicación:**
- **`Handlebars.create()`** crea una instancia **aislada**: los parciales no se mezclan con otros usos globales de Handlebars.
- **Las plantillas se compilan una sola vez, en el constructor.** Compilar es lento y renderizar es rápido. Si falta un `.hbs`, el servicio no arranca.
- **`__dirname + '/views'`** funciona igual en las pruebas (`src/templates/views`) y en producción (`dist/templates/views`, gracias al Paso 10).
- **El asunto se compila con `noEscape: true`:** es texto plano, y escaparlo convertiría `Java & Spring` en `Java &amp; Spring`.

### 9.6 Pruebas de renderizado (CA 1.4)

📄 **Crear** `notifications-service/src/templates/handlebars-template.renderer.spec.ts`

```ts
import { ConfigService } from '@nestjs/config';
import { TEMPLATE_KEYS } from '../domain/notification';
import type { NotificationEvent } from '../domain/notification-event';
import { FIXTURE_NAMES, loadFixture } from '../test-support/load-fixture';
import { HandlebarsTemplateRenderer } from './handlebars-template.renderer';

const renderer = new HandlebarsTemplateRenderer(
  new ConfigService({ FRONTEND_URL: 'https://app.test/' }),
);
const fixture = (name: (typeof FIXTURE_NAMES)[number]): NotificationEvent =>
  loadFixture(name) as NotificationEvent;

describe('HandlebarsTemplateRenderer', () => {
  it.each(FIXTURE_NAMES)(
    'renderiza todas las plantillas con el evento %s sin variables faltantes',
    (name) => {
      const event = fixture(name);
      for (const key of TEMPLATE_KEYS) {
        const email = renderer.render(key, event, 'Administrador SkillBridge');
        expect(email.subject.length).toBeGreaterThan(5);
        expect(email.html).toContain('<!doctype html>');
        expect(email.html).not.toContain('{{');
        expect(email.text).not.toMatch(/<[a-z]/i);
        expect(email.html).not.toContain('app.test//');
      }
    },
  );

  it('escapa HTML en los datos del usuario (anti-inyección)', () => {
    const event = fixture('user.registered');
    event.subject.name = '<script>alert(1)</script>';
    const email = renderer.render('welcome', event, event.subject.name);
    expect(email.html).not.toContain('<script>');
    expect(email.html).toContain('&lt;script&gt;');
  });

  it('no escapa el asunto (es texto plano)', () => {
    const event = fixture('enrollment.created');
    event.data.serviceName = 'Java & Spring';
    expect(renderer.render('booking-confirmed', event, 'Ana').subject).toBe(
      'Reserva confirmada: Java & Spring',
    );
  });

  it('el correo de cuenta desactivada no incluye botón de login', () => {
    const event = fixture('user.status.changed');
    const email = renderer.render('status-changed', event, event.subject.name);
    expect(email.subject).toContain('desactivada');
    expect(email.html).not.toContain('/login');
  });

  it('la confirmación de reserva muestra la fecha en español sin correrse de día', () => {
    const email = renderer.render(
      'booking-confirmed',
      fixture('enrollment.created'),
      'Ana',
    );
    expect(email.html).toContain('20 de noviembre de 2026');
    expect(email.text).toContain('Ver mis reservas: https://app.test/bookings');
  });

  it('nunca menciona contraseñas concretas en account-created', () => {
    const event = fixture('user.created');
    const email = renderer.render('account-created', event, event.subject.name);
    expect(email.text).not.toMatch(/TemporaryPass/i);
  });
});
```

**Explicación:**
- Se renderizan **las 10 plantillas con los 6 eventos del contrato**. Con el modo `strict`, cualquier variable mal escrita rompe la prueba.
- Hay pruebas de seguridad: el HTML se escapa y no aparecen contraseñas.
- La fecha de la reserva sale en español y **sin correrse un día**.

## Paso 10: Copiar las plantillas al compilar

📝 **Reemplazar** `notifications-service/nest-cli.json`

```json
{
  "$schema": "https://json.schemastore.org/nest-cli",
  "collection": "@nestjs/schematics",
  "sourceRoot": "src",
  "compilerOptions": {
    "deleteOutDir": true,
    "assets": [{ "include": "templates/views/**/*.hbs", "watchAssets": true }]
  }
}
```

**Explicación:** TypeScript sólo compila `.ts`. La opción `assets` le indica a Nest que **copie** los `.hbs` a `dist/templates/views/`. Sin esto, la imagen Docker arranca y falla con `ENOENT … layout.hbs`. `watchAssets` recarga las plantillas en `npm run start:dev`.

### Vista previa en el navegador (opcional, muy útil)

```bash
cd notifications-service
npm run build
mkdir -p ../.preview-correos
node -e "
const { HandlebarsTemplateRenderer } = require('./dist/templates/handlebars-template.renderer');
const { ConfigService } = require('@nestjs/config');
const fs = require('fs');
const r = new HandlebarsTemplateRenderer(new ConfigService({ FRONTEND_URL: 'http://localhost:8088' }));
const pairs = [['user.registered','welcome'],['user.registered','admin.new-registration'],['user.created','account-created'],['user.status.changed','status-changed'],['user.status.changed','admin.status-changed'],['user.role.changed','role-changed'],['user.deleted','account-deleted'],['user.deleted','admin.account-deleted'],['enrollment.created','booking-confirmed'],['enrollment.created','provider.new-enrollment']];
for (const [ev, tpl] of pairs) {
  const e = JSON.parse(fs.readFileSync('../docs/notifications/contract/' + ev + '.json', 'utf8'));
  fs.writeFileSync('../.preview-correos/' + tpl + '.html', r.render(tpl, e, 'Administrador SkillBridge').html);
}
console.log('Listo: abre .preview-correos/*.html');
"
xdg-open ../.preview-correos/booking-confirmed.html
```

> No subas `.preview-correos/` a git. Bórrala al terminar (`rm -rf ../.preview-correos`).

## Paso 11 (CA 1.1): Docker y docker-compose

📄 **Crear** `notifications-service/Dockerfile`

```dockerfile
# ---------- Etapa 1: compilar TypeScript ----------
FROM public.ecr.aws/docker/library/node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci --no-audit --no-fund
COPY tsconfig*.json nest-cli.json ./
COPY src ./src
RUN npm run build

# ---------- Etapa 2: imagen final liviana ----------
FROM public.ecr.aws/docker/library/node:22-alpine
ENV NODE_ENV=production
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev --no-audit --no-fund && npm cache clean --force
COPY --from=builder /app/dist ./dist
USER node
EXPOSE 3100
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=5 \
  CMD wget -qO- http://127.0.0.1:3100/health/live >/dev/null || exit 1
CMD ["node", "dist/main.js"]
```

📄 **Crear** `notifications-service/.dockerignore`

```
node_modules
dist
coverage
.env
*.log
test
```

**Explicación:**
- **Dos etapas (*multi-stage*):** la primera compila con todas las herramientas; la segunda sólo lleva `dist/` y las dependencias de producción. La imagen queda más liviana y segura.
- `public.ecr.aws/docker/library/node:22-alpine` es el mismo registro que usan `backend/Dockerfile` y `frontend/Dockerfile`, y evita el límite de descargas de Docker Hub.
- **`USER node`:** el proceso no corre como root.
- **`HEALTHCHECK`** usa `/health/live`, que existe desde la base.
- **`.dockerignore`** evita copiar `.env`: los secretos **nunca** deben quedar dentro de la imagen.

📝 **Modificar** `docker-compose.yml` (raíz). Agrega este servicio **después** de `frontend:`, al mismo nivel de indentación:

```yaml
  notifications:
    build:
      context: ./notifications-service
    environment:
      NODE_ENV: production
      PORT: 3100
      RABBITMQ_URL: amqp://guest:guest@rabbitmq:5672
      REDIS_URL: redis://redis:6379/1
      NOTIFICATIONS_DATABASE_URL: postgresql://${POSTGRES_USER:-skillbridge}:${POSTGRES_PASSWORD:-skillbridge_dev}@postgres:5432/notifications
      EMAIL_DRIVER: ${EMAIL_DRIVER:-log}
      RESEND_API_KEY: ${RESEND_API_KEY:-}
      RESEND_WEBHOOK_SECRET: ${RESEND_WEBHOOK_SECRET:-}
      MAIL_FROM: ${MAIL_FROM:-SkillBridge AI <onboarding@resend.dev>}
      MAIL_REPLY_TO: ${MAIL_REPLY_TO:-}
      MAIL_REDIRECT_TO: ${MAIL_REDIRECT_TO:-}
      FRONTEND_URL: ${FRONTEND_URL:-http://localhost:8088}
    depends_on:
      rabbitmq:
        condition: service_healthy
      redis:
        condition: service_healthy
      postgres:
        condition: service_healthy
    ports:
      - "127.0.0.1:3100:3100"
    healthcheck:
      test: [ "CMD-SHELL", "wget -qO- http://127.0.0.1:3100/health/live >/dev/null || exit 1" ]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 30s
    restart: unless-stopped
    networks: [ skillbridge ]
```

**Explicación:**
- Dentro de la red `skillbridge`, los contenedores se encuentran por **nombre de servicio** (`rabbitmq`, `redis`, `postgres`).
- **`redis://redis:6379/1`:** se usa la **base 1** de Redis. El backend usa la 0 para su caché, así no se mezclan llaves.
- **`/notifications`** al final de la URL de Postgres: es una **base de datos propia** del microservicio dentro del mismo contenedor. La crea Leonardo con `make db-notifications`.
- Si la contraseña de Postgres tiene caracteres especiales (`@`, `:`, `/`, `#`), hay que codificarlos en la URL (por ejemplo, `@` se escribe `%40`).
- **`${VAR:-valor}`:** toma el valor del `.env` de la raíz o usa el de la derecha. `make up` funciona **sin** API key.
- Se declaran **todas** las variables (también las de Kevin y Leonardo), así nadie más edita este bloque.

📝 **Modificar** `.env.example` (raíz). Agrega al final:

```dotenv
# ---------- notifications-service ----------
# log = no envía correos, sólo los escribe en el log (por defecto). resend = envío real.
EMAIL_DRIVER=log
# API key de Resend (https://resend.com/api-keys). Permiso "Sending access". NUNCA la subas a git.
RESEND_API_KEY=
# Secreto de firma del webhook de Resend (empieza por whsec_). Ver CONFIGURACION-DOMINIO-Y-SECRETOS.md §9
RESEND_WEBHOOK_SECRET=
# Remitente de un dominio VERIFICADO. Mientras no lo esté: SkillBridge AI <onboarding@resend.dev>
MAIL_FROM=SkillBridge AI <notificaciones@mail.tudominio.com>
MAIL_REPLY_TO=
# Sólo desarrollo: redirige TODOS los correos a esta dirección
MAIL_REDIRECT_TO=
FRONTEND_URL=http://localhost:8088
```

## Paso 12: Verificar y subir

```bash
cd notifications-service
npm run format && npm run lint:check && npm test && npm run build
ls dist/templates/views        # deben estar los .hbs

cd ..
git add notifications-service docker-compose.yml .env.example
git status                     # NO debe aparecer .env
git commit -m "feat(notificaciones): adaptador Resend, motor de plantillas Handlebars, Docker y compose (KAN-56)"
git push -u origin feature/notificaciones-resend-templates
gh pr create --base feature/notificaciones --head feature/notificaciones-resend-templates \
  --title "feat(notificaciones): Resend + plantillas Handlebars + Docker (KAN-56)" \
  --body "Cierra CA 1.1–1.4 de KAN-56: ResendModule y adaptador con idempotencia y clasificación de errores, 10 plantillas Handlebars (incluye confirmación de reserva), Dockerfile multi-stage y servicio en docker-compose."
```

## Definition of Done (KAN-56)

- [ ] La base está mergeada y Kevin y Leonardo trabajan sobre ella.
- [ ] `npm test`, `npm run lint:check` y `npm run build` en verde.
- [ ] `dist/templates/views/*.hbs` existen después del build.
- [ ] `make up` levanta `notifications` en estado *healthy* sin API key.
- [ ] Con `EMAIL_DRIVER=resend` llega un correo real (bienvenida y confirmación de reserva).
- [ ] Las plantillas se ven bien en Gmail (escritorio y móvil).
- [ ] Entrada en `MEMORIA.md` y KAN-56 movida a "En revisión".

## Prompt para otra IA

```text
Trabajo en notifications-service (NestJS 11 CommonJS, TypeScript estricto, Node 22) del proyecto
SkillBridge AI, historia Jira KAN-56: scaffolding, ResendModule (SDK resend@6) y motor de plantillas
Handlebars. Arquitectura hexagonal: src/domain (puro), src/application (casos de uso + puertos como
clases abstractas), src/infrastructure (adaptadores). Contrato de eventos: docs/notifications/CONTRACT.md.
Guía: docs/notifications/GUIA-DYLAN-HU-NOT01-KAN-56.md. Versiones fijadas: @nestjs/config@4,
@golevelup/nestjs-rabbitmq@6, typeorm@0.3 (las más nuevas son ESM y rompen Jest).
Reglas: nunca exponer RESEND_API_KEY, no hacer git commit/push, registrar en MEMORIA.md.
Estoy en el Paso <N> y obtengo: <error>. Explícame la causa y dame el código corregido bloque por bloque.
```
